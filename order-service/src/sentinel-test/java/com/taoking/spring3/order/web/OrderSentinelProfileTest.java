package com.taoking.spring3.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

@ActiveProfiles("sentinel")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.order.heartbeat-delay=PT1H",
        "demo.sentinel.flow.qps=1",
        "demo.sentinel.hot-sku.qps=1",
        "demo.sentinel.hot-sku.duration=1s",
        "demo.sentinel.degrade.slow-threshold=10ms",
        "demo.sentinel.degrade.slow-ratio-threshold=0.5",
        "demo.sentinel.degrade.minimum-request-amount=2",
        "demo.sentinel.degrade.stat-interval=1s",
        "demo.sentinel.degrade.time-window=3s",
        "demo.sentinel.degrade.probe-delay=50ms",
        "management.zipkin.tracing.export.enabled=false",
        "resilience4j.circuitbreaker.instances.catalog-service.minimum-number-of-calls=10"
})
class OrderSentinelProfileTest {

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

    @BeforeEach
    void waitForRuleWindow() throws InterruptedException {
        Thread.sleep(1100);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("demo.clients.catalog.base-url", () -> catalogServer.url("/").toString().replaceAll("/$", ""));
    }

    @Test
    @Order(1)
    void previewStillWorksWhenSentinelProfileIsEnabled() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":301,"sku":"SKU-SENTINEL-OK","name":"Sentinel Product","price":49.00,"active":true,"fallback":false}
                """));

        ResponseEntity<OrderPreviewResponse> response = postPreview(
                "/api/orders/preview",
                new OrderPreviewRequest("SKU-SENTINEL-OK", 1)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fallbackUsed()).isFalse();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-SENTINEL-OK?slow=false&fail=false");
    }

    @Test
    @Order(2)
    void previewFlowRuleRejectsSecondFastCall() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":302,"sku":"SKU-SENTINEL-FLOW-1","name":"Flow Product","price":59.00,"active":true,"fallback":false}
                """));

        ResponseEntity<OrderPreviewResponse> first = postPreview(
                "/api/orders/preview?sentinelFlow=true",
                new OrderPreviewRequest("SKU-SENTINEL-FLOW-1", 1)
        );
        ResponseEntity<String> blocked = postPreviewForText(
                "/api/orders/preview?sentinelFlow=true",
                new OrderPreviewRequest("SKU-SENTINEL-FLOW-2", 1)
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().fallbackUsed()).isFalse();
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody())
                .contains("Sentinel request blocked")
                .contains("order-preview-flow")
                .contains("FLOW");
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-SENTINEL-FLOW-1?slow=false&fail=false");
        assertThat(catalogServer.takeRequest(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @Order(3)
    void hotSkuRuleRejectsSameSkuSecondFastCall() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":303,"sku":"SKU-SENTINEL-HOT","name":"Hot Product","price":69.00,"active":true,"fallback":false}
                """));

        ResponseEntity<OrderPreviewResponse> first = postPreview(
                "/api/orders/preview?sentinelHotSku=true",
                new OrderPreviewRequest("SKU-SENTINEL-HOT", 1)
        );
        ResponseEntity<String> blocked = postPreviewForText(
                "/api/orders/preview?sentinelHotSku=true",
                new OrderPreviewRequest("SKU-SENTINEL-HOT", 1)
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().fallbackUsed()).isFalse();
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody())
                .contains("Sentinel request blocked")
                .contains("order-preview-hot-sku")
                .contains("HOT_PARAM");
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-SENTINEL-HOT?slow=false&fail=false");
        assertThat(catalogServer.takeRequest(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @Order(4)
    void degradeProbeOpensAfterSlowCalls() {
        ResponseEntity<String> first = getText("/api/orders/sentinel/degrade-probe?slow=true");
        ResponseEntity<String> second = getText("/api/orders/sentinel/degrade-probe?slow=true");
        ResponseEntity<String> blocked = getText("/api/orders/sentinel/degrade-probe?slow=true");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).contains("slow-call");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).contains("slow-call");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody())
                .contains("Sentinel request blocked")
                .contains("order-catalog-degrade-probe")
                .contains("DEGRADE");
    }

    private ResponseEntity<OrderPreviewResponse> postPreview(String path, OrderPreviewRequest request) {
        return restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(url(path), request, OrderPreviewResponse.class);
    }

    private ResponseEntity<String> postPreviewForText(String path, OrderPreviewRequest request) {
        return restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(url(path), request, String.class);
    }

    private ResponseEntity<String> getText(String path) {
        return restTemplate
                .withBasicAuth("user", "user123")
                .getForEntity(url(path), String.class);
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
