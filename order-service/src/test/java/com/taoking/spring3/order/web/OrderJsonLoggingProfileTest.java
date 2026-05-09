package com.taoking.spring3.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("json-logging")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.order.heartbeat-delay=PT1H",
        "management.zipkin.tracing.export.enabled=false",
        "resilience4j.circuitbreaker.instances.catalog-service.minimum-number-of-calls=10"
})
class OrderJsonLoggingProfileTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static MockWebServer catalogServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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
        registry.add("demo.clients.catalog.base-url", () -> catalogServer.url("/").toString().replaceAll("/$", ""));
    }

    @Test
    void jsonLoggingAddsRequestIdAndMasksSensitiveHeaders(CapturedOutput output) throws Exception {
        catalogServer.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"id":401,"sku":"SKU-JSON-LOG","name":"Logging Product","price":79.00,"active":true,"fallback":false}
                        """));

        String rawAuthorization = "Basic dXNlcjp1c2VyMTIz";
        ResponseEntity<OrderPreviewResponse> response = restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(
                        url("/api/orders/preview"),
                        new OrderPreviewRequest("SKU-JSON-LOG", 1),
                        OrderPreviewResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();

        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-JSON-LOG?slow=false&fail=false");

        List<JsonNode> requestLogs = output.getOut()
                .lines()
                .filter(line -> line.contains("http request completed"))
                .map(this::readJson)
                .toList();

        assertThat(requestLogs).isNotEmpty();
        JsonNode requestLog = requestLogs.getLast();
        assertThat(requestLog.path("application").asText()).isEqualTo("order-service");
        assertThat(requestLog.path("requestId").asText()).isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(requestLog.path("status").asInt()).isEqualTo(200);
        assertThat(requestLog.path("elapsedMs").isNumber()).isTrue();
        assertThat(requestLog.path("authScheme").asText()).isEqualTo("Basic");
        assertThat(output.getOut()).doesNotContain(rawAuthorization);
        assertThat(output.getOut()).doesNotContain("user123");
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Expected JSON log line: " + line, ex);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
