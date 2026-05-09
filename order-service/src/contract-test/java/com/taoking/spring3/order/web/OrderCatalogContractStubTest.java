package com.taoking.spring3.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@AutoConfigureStubRunner(
        ids = "com.taoking.spring3:catalog-service:+:stubs:18081",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.clients.catalog.base-url=http://localhost:18081",
        "demo.order.heartbeat-delay=PT1H",
        "management.zipkin.tracing.export.enabled=false",
        "resilience4j.circuitbreaker.instances.catalog-service.minimum-number-of-calls=10"
})
class OrderCatalogContractStubTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> circuitBreaker.reset());
    }

    @Test
    void previewUsesCatalogProductStub() {
        ResponseEntity<OrderPreviewResponse> response = preview("SKU-1001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fallbackUsed()).isFalse();
        assertThat(response.getBody().product().sku()).isEqualTo("SKU-1001");
        assertThat(response.getBody().product().fallback()).isFalse();
    }

    @Test
    void previewUsesFallbackWhenCatalogProductIsMissing() {
        ResponseEntity<OrderPreviewResponse> response = preview("UNKNOWN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fallbackUsed()).isTrue();
        assertThat(response.getBody().product().sku()).isEqualTo("UNKNOWN");
        assertThat(response.getBody().product().fallback()).isTrue();
    }

    @Test
    void previewUsesFallbackWhenCatalogFails() {
        ResponseEntity<OrderPreviewResponse> response = restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(
                        url("/api/orders/preview?failCatalog=true"),
                        new OrderPreviewRequest("SKU-FAIL", 1),
                        OrderPreviewResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fallbackUsed()).isTrue();
        assertThat(response.getBody().product().sku()).isEqualTo("SKU-FAIL");
        assertThat(response.getBody().product().fallback()).isTrue();
    }

    private ResponseEntity<OrderPreviewResponse> preview(String sku) {
        return restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(
                        url("/api/orders/preview"),
                        new OrderPreviewRequest(sku, 1),
                        OrderPreviewResponse.class
                );
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
