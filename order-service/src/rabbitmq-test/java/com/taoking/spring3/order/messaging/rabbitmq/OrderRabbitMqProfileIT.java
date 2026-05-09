package com.taoking.spring3.order.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("rabbitmq")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.order.heartbeat-delay=PT1H",
        "management.zipkin.tracing.export.enabled=false",
        "resilience4j.circuitbreaker.instances.catalog-service.minimum-number-of-calls=10",
        "spring.rabbitmq.listener.simple.retry.max-attempts=2",
        "spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=50ms",
        "logging.level.org.springframework.amqp.rabbit.retry=ERROR",
        "logging.level.org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler=ERROR"
})
class OrderRabbitMqProfileIT {

    private static final DockerImageName RABBITMQ_IMAGE = DockerImageName.parse("rabbitmq:3.13-management");
    private static final String RABBITMQ_USERNAME = "spring3";
    private static final String RABBITMQ_PASSWORD = "spring3";

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer(RABBITMQ_IMAGE)
            .withAdminUser(RABBITMQ_USERNAME)
            .withAdminPassword(RABBITMQ_PASSWORD);

    private static MockWebServer catalogServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitOrderMessagingProperties properties;

    @Autowired
    private RabbitOrderPreviewConsumer consumer;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void startCatalogServer() throws IOException {
        catalogServer = new MockWebServer();
        catalogServer.start();
    }

    @AfterAll
    static void stopCatalogServer() throws IOException {
        catalogServer.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
        registry.add("demo.clients.catalog.base-url", () -> catalogServer.url("/").toString().replaceAll("/$", ""));
    }

    @BeforeEach
    void resetState() {
        consumer.resetState();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> circuitBreaker.reset());
        amqpAdmin.purgeQueue(properties.queue(), true);
        amqpAdmin.purgeQueue(properties.deadLetterQueue(), true);
    }

    @Test
    void previewPublishesAndConsumesRabbitMqMessage() throws Exception {
        catalogServer.enqueue(productResponse("SKU-RABBITMQ-OK"));

        ResponseEntity<OrderPreviewResponse> response = postPreview("SKU-RABBITMQ-OK");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        String eventId = response.getBody().orderId();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(consumer.hasProcessed(eventId)).isTrue());
        assertThat(consumer.processedEventCount()).isEqualTo(1);

        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-RABBITMQ-OK?slow=false&fail=false");
    }

    @Test
    void duplicateMessageIsSkippedByEventId() {
        String eventId = "manual-" + UUID.randomUUID();
        OrderPreviewMessage message = new OrderPreviewMessage(
                eventId,
                eventId,
                "SKU-RABBITMQ-IDEMPOTENT",
                2,
                BigDecimal.valueOf(198),
                false,
                Instant.now()
        );

        publish(message);
        publish(message);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(consumer.hasProcessed(eventId)).isTrue();
            assertThat(consumer.processedEventCount()).isEqualTo(1);
            assertThat(consumer.duplicateEventCount()).isEqualTo(1);
        });
    }

    @Test
    void poisonSkuIsRetriedAndDeadLettered() {
        catalogServer.enqueue(productResponse("SKU-RABBITMQ-FAIL"));

        ResponseEntity<OrderPreviewResponse> response = postPreview("SKU-RABBITMQ-FAIL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        String eventId = response.getBody().orderId();
        Message deadLetter = receiveDeadLetter(eventId);
        String body = new String(deadLetter.getBody(), StandardCharsets.UTF_8);
        assertThat(body).contains(eventId);
        assertThat(body).contains("SKU-RABBITMQ-FAIL");
        assertThat(consumer.hasProcessed(eventId)).isFalse();
    }

    private ResponseEntity<OrderPreviewResponse> postPreview(String sku) {
        return restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(url("/api/orders/preview"), new OrderPreviewRequest(sku, 2), OrderPreviewResponse.class);
    }

    private void publish(OrderPreviewMessage message) {
        rabbitTemplate.convertAndSend(properties.exchange(), properties.routingKey(), message, rabbitMessage -> {
            rabbitMessage.getMessageProperties().setMessageId(message.eventId());
            rabbitMessage.getMessageProperties().setHeader("eventId", message.eventId());
            rabbitMessage.getMessageProperties().setHeader("eventType", "OrderPreviewCreated");
            return rabbitMessage;
        });
    }

    private Message receiveDeadLetter(String eventId) {
        Message[] received = new Message[1];
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message message = rabbitTemplate.receive(properties.deadLetterQueue(), 500);
            assertThat(message).isNotNull();
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).contains(eventId);
            received[0] = message;
        });
        return received[0];
    }

    private MockResponse productResponse(String sku) {
        String body = """
                {"id":501,"sku":"%s","name":"RabbitMQ Product","price":99.00,"active":true,"fallback":false}
                """.formatted(sku);
        return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
