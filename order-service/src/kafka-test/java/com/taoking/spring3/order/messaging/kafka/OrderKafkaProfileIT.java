package com.taoking.spring3.order.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.taoking.spring3.common.api.ApiHeaders;
import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("kafka")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.order.heartbeat-delay=PT1H",
        "management.zipkin.tracing.export.enabled=false",
        "resilience4j.circuitbreaker.instances.catalog-service.minimum-number-of-calls=10",
        "demo.messaging.kafka.retry-backoff=50ms",
        "demo.messaging.kafka.retry-max-attempts=1",
        "demo.messaging.kafka.listener-concurrency=1",
        "logging.level.org.apache.kafka=ERROR",
        "logging.level.org.springframework.kafka=ERROR"
})
class OrderKafkaProfileIT {

    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.6.1");
    private static final String TOPIC = "spring3.order-preview.events.it-" + UUID.randomUUID();
    private static final String RETRY_TOPIC = TOPIC + ".retry";
    private static final String DLT_TOPIC = TOPIC + ".dlt";
    private static final String GROUP_ID = "spring3-order-preview-it-" + UUID.randomUUID();

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(KAFKA_IMAGE);

    private static MockWebServer catalogServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KafkaTemplate<String, OrderPreviewKafkaEvent> kafkaTemplate;

    @Autowired
    private KafkaOrderMessagingProperties properties;

    @Autowired
    private KafkaOrderPreviewConsumer consumer;

    @Autowired
    private InMemoryProcessedKafkaEventStore processedEventStore;

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
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("demo.messaging.kafka.topic", () -> TOPIC);
        registry.add("demo.messaging.kafka.retry-topic", () -> RETRY_TOPIC);
        registry.add("demo.messaging.kafka.dead-letter-topic", () -> DLT_TOPIC);
        registry.add("demo.messaging.kafka.consumer-group", () -> GROUP_ID);
        registry.add("demo.clients.catalog.base-url", () -> catalogServer.url("/").toString().replaceAll("/$", ""));
    }

    @BeforeEach
    void resetState() {
        consumer.resetState();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> circuitBreaker.reset());
    }

    @Test
    void previewPublishesAndConsumesKafkaEventWithRequestHeaders() throws Exception {
        catalogServer.enqueue(productResponse("SKU-KAFKA-OK"));
        String requestId = "kafka-request-" + UUID.randomUUID();
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";

        ResponseEntity<OrderPreviewResponse> response = postPreview("SKU-KAFKA-OK", requestId, traceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        String eventId = response.getBody().orderId();
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(consumer.hasProcessed(eventId)).isTrue());
        OrderPreviewKafkaEvent event = consumer.processedEvent(eventId);
        assertThat(event.requestId()).isEqualTo(requestId);
        assertThat(event.traceId()).isEqualTo(traceId);
        assertThat(event.payload().sku()).isEqualTo("SKU-KAFKA-OK");
        assertThat(consumer.processedEventCount()).isEqualTo(1);

        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-KAFKA-OK?slow=false&fail=false");
    }

    @Test
    void duplicateKafkaEventIsSkippedByEventId() throws Exception {
        String eventId = "manual-" + UUID.randomUUID();
        OrderPreviewKafkaEvent event = manualEvent(eventId, eventId, "SKU-KAFKA-IDEMPOTENT");

        publish(event);
        publish(event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(consumer.hasProcessed(eventId)).isTrue();
            assertThat(consumer.processedEventCount()).isEqualTo(1);
            assertThat(consumer.duplicateEventCount()).isEqualTo(1);
        });
    }

    @Test
    void sameKeyKafkaEventsAreConsumedInOrder() throws Exception {
        String partitionKey = "order-sequence-" + UUID.randomUUID();
        OrderPreviewKafkaEvent first = manualEvent(partitionKey + "-1", partitionKey, "SKU-KAFKA-ORDERED");
        OrderPreviewKafkaEvent second = manualEvent(partitionKey + "-2", partitionKey, "SKU-KAFKA-ORDERED");
        OrderPreviewKafkaEvent third = manualEvent(partitionKey + "-3", partitionKey, "SKU-KAFKA-ORDERED");

        publish(first);
        publish(second);
        publish(third);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(processedEventStore.processedEventIdsForKey(partitionKey))
                        .containsExactly(first.eventId(), second.eventId(), third.eventId()));
    }

    @Test
    void poisonSkuIsRetriedAndPublishedToDeadLetterTopic() {
        catalogServer.enqueue(productResponse("SKU-KAFKA-FAIL"));

        ResponseEntity<OrderPreviewResponse> response = postPreview("SKU-KAFKA-FAIL", "kafka-poison", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        String eventId = response.getBody().orderId();
        ConsumerRecord<String, String> deadLetter = receiveDeadLetter(eventId);
        assertThat(deadLetter.value()).contains(eventId);
        assertThat(deadLetter.value()).contains("SKU-KAFKA-FAIL");
        assertThat(headerValue(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(properties.topic());
        assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION)).isNotNull();
        assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();
        assertThat(headerValue(deadLetter, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .isEqualTo(ListenerExecutionFailedException.class.getName());
        assertThat(headerValue(deadLetter, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(IllegalStateException.class.getName());
        assertThat(consumer.hasProcessed(eventId)).isFalse();
    }

    private ResponseEntity<OrderPreviewResponse> postPreview(String sku, String requestId, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("user", "user123");
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (requestId != null) {
            headers.set(ApiHeaders.REQUEST_ID, requestId);
        }
        if (traceId != null) {
            headers.set("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01");
        }
        return restTemplate.postForEntity(
                url("/api/orders/preview"),
                new HttpEntity<>(new OrderPreviewRequest(sku, 2), headers),
                OrderPreviewResponse.class
        );
    }

    private void publish(OrderPreviewKafkaEvent event) throws Exception {
        kafkaTemplate.send(properties.topic(), event.partitionKey(), event).get(10, TimeUnit.SECONDS);
    }

    private ConsumerRecord<String, String> receiveDeadLetter(String eventId) {
        try (KafkaConsumer<String, String> deadLetterConsumer = new KafkaConsumer<>(deadLetterConsumerProperties())) {
            deadLetterConsumer.subscribe(List.of(properties.deadLetterTopic()));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = deadLetterConsumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains(eventId)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No Kafka DLT record found for eventId=" + eventId);
    }

    private Map<String, Object> deadLetterConsumerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "spring3-order-preview-dlt-reader-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return properties;
    }

    private String headerValue(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private OrderPreviewKafkaEvent manualEvent(String eventId, String partitionKey, String sku) {
        return new OrderPreviewKafkaEvent(
                eventId,
                OrderPreviewKafkaEvent.EVENT_TYPE,
                OrderPreviewKafkaEvent.EVENT_VERSION,
                OrderPreviewKafkaEvent.SOURCE,
                Instant.now(),
                OrderPreviewKafkaEvent.AGGREGATE_TYPE,
                partitionKey,
                partitionKey,
                "manual-request",
                "manual-trace",
                new OrderPreviewKafkaEvent.Payload(
                        partitionKey,
                        sku,
                        2,
                        BigDecimal.valueOf(198),
                        false
                )
        );
    }

    private MockResponse productResponse(String sku) {
        String body = """
                {"id":601,"sku":"%s","name":"Kafka Product","price":99.00,"active":true,"fallback":false}
                """.formatted(sku);
        return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
