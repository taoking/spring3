package com.taoking.spring3.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.order.heartbeat-delay=PT1H",
        "demo.resilience.catalog.bulkhead-hold-duration=700ms",
        "management.tracing.sampling.probability=1.0",
        "management.zipkin.tracing.export.enabled=false",
        "resilience4j.retry.instances.catalog-service.wait-duration=10ms",
        "resilience4j.circuitbreaker.instances.catalog-service.minimum-number-of-calls=10",
        "resilience4j.timelimiter.instances.catalog-service.timeout-duration=200ms",
        "resilience4j.ratelimiter.instances.catalog-rate-limit.limit-refresh-period=30s"
})
@AutoConfigureObservability
class OrderResilience4jTest {

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
    void failCatalogTriggersRetryFallbackAndMetrics() throws Exception {
        for (int i = 0; i < 3; i++) {
            catalogServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""
                            {"title":"Simulated catalog failure","status":500}
                            """));
        }

        ResponseEntity<OrderPreviewResponse> response = postPreview(
                "/api/orders/preview?failCatalog=true",
                new OrderPreviewRequest("SKU-R4J-FAIL", 1)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fallbackUsed()).isTrue();
        for (int i = 0; i < 3; i++) {
            RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
            assertThat(catalogRequest).isNotNull();
            assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-R4J-FAIL?slow=false&fail=true");
        }
        assertPrometheusContains("resilience4j_retry_calls_total", "resilience4j_circuitbreaker_calls_seconds_count");
    }

    @Test
    void slowCatalogTriggersTimeLimiterFallbackAndMetrics() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":201,"sku":"SKU-R4J-SLOW","name":"Slow Product","price":19.00,"active":true,"fallback":false}
                """).setHeadersDelay(1, TimeUnit.SECONDS));

        ResponseEntity<OrderPreviewResponse> response = postPreview(
                "/api/orders/preview?slowCatalog=true",
                new OrderPreviewRequest("SKU-R4J-SLOW", 1)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fallbackUsed()).isTrue();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-R4J-SLOW?slow=true&fail=false");
        assertPrometheusContains("resilience4j_timelimiter_calls_total");
    }

    @Test
    void rateLimitProbeRejectsSecondFastCallAndExposesMetrics() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":202,"sku":"SKU-R4J-RATE","name":"Rate Product","price":29.00,"active":true,"fallback":false}
                """));

        ResponseEntity<OrderPreviewResponse> first = postPreview(
                "/api/orders/preview?rateLimit=true",
                new OrderPreviewRequest("SKU-R4J-RATE", 1)
        );
        ResponseEntity<OrderPreviewResponse> second = postPreview(
                "/api/orders/preview?rateLimit=true",
                new OrderPreviewRequest("SKU-R4J-RATE-SECOND", 1)
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().fallbackUsed()).isFalse();
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().fallbackUsed()).isTrue();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-R4J-RATE?slow=false&fail=false");
        assertPrometheusContains("resilience4j_ratelimiter_available_permissions");
    }

    @Test
    void bulkheadProbeRejectsConcurrentCallAndExposesMetrics() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":203,"sku":"SKU-R4J-BULKHEAD","name":"Bulkhead Product","price":39.00,"active":true,"fallback":false}
                """));

        CompletableFuture<ResponseEntity<OrderPreviewResponse>> firstCall = CompletableFuture.supplyAsync(() -> postPreview(
                "/api/orders/preview?bulkhead=true&holdBulkhead=true",
                new OrderPreviewRequest("SKU-R4J-BULKHEAD", 1)
        ));
        Thread.sleep(150);
        ResponseEntity<OrderPreviewResponse> second = postPreview(
                "/api/orders/preview?bulkhead=true&holdBulkhead=true",
                new OrderPreviewRequest("SKU-R4J-BULKHEAD-SECOND", 1)
        );
        ResponseEntity<OrderPreviewResponse> first = firstCall.get(2, TimeUnit.SECONDS);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().fallbackUsed()).isFalse();
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().fallbackUsed()).isTrue();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-R4J-BULKHEAD?slow=false&fail=false");
        assertPrometheusContains("resilience4j_bulkhead_available_concurrent_calls");
    }

    private ResponseEntity<OrderPreviewResponse> postPreview(String path, OrderPreviewRequest request) {
        return restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(url(path), request, OrderPreviewResponse.class);
    }

    private void assertPrometheusContains(String... expected) {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/prometheus"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String metricName : expected) {
            assertThat(response.getBody()).contains(metricName);
        }
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
