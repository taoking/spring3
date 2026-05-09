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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.order.heartbeat-delay=PT1H",
        "management.tracing.sampling.probability=1.0",
        "management.zipkin.tracing.export.enabled=false"
})
@AutoConfigureObservability
class OrderControllerTest {

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
    void healthIsPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void previewRequiresAuthentication() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/orders/preview"),
                new OrderPreviewRequest("SKU-1001", 1),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void previewReturnsOrderPreview() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":1,"sku":"SKU-1001","name":"Spring Boot 3 Guide","price":99.00,"active":true,"fallback":false}
                """));

        ResponseEntity<OrderPreviewResponse> response = restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(
                        url("/api/orders/preview"),
                        new OrderPreviewRequest("SKU-1001", 2),
                        OrderPreviewResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().product().sku()).isEqualTo("SKU-1001");
        assertThat(response.getBody().quantity()).isEqualTo(2);
        assertThat(response.getBody().fallbackUsed()).isFalse();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-1001?slow=false&fail=false");
    }

    @Test
    void previewPropagatesW3cTraceContextToCatalogClient() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":2,"sku":"SKU-1002","name":"Observability Workbook","price":129.00,"active":true,"fallback":false}
                """));
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("user", "user123");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01");

        ResponseEntity<OrderPreviewResponse> response = restTemplate.exchange(
                url("/api/orders/preview"),
                HttpMethod.POST,
                new HttpEntity<>(new OrderPreviewRequest("SKU-1002", 2), headers),
                OrderPreviewResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getHeader("traceparent")).contains(traceId);
    }

    @Test
    void validationFailureUsesProblemDetail() {
        ResponseEntity<String> response = restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(
                        url("/api/orders/preview"),
                        new OrderPreviewRequest("", 0),
                        String.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Validation failed");
        assertThat(response.getBody()).contains("fieldErrors");
    }

    @Test
    void catalogFailureUsesFallback() {
        catalogServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"title":"Simulated catalog failure","status":500}
                        """));

        ResponseEntity<OrderPreviewResponse> response = restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(
                        url("/api/orders/preview?failCatalog=true"),
                        new OrderPreviewRequest("SKU-1001", 2),
                        OrderPreviewResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fallbackUsed()).isTrue();
        assertThat(response.getBody().product().fallback()).isTrue();
    }

    @Test
    void adminStatsRequiresAdminRole() {
        ResponseEntity<String> forbidden = restTemplate
                .withBasicAuth("user", "user123")
                .getForEntity(url("/api/orders/admin/stats"), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> allowed = restTemplate
                .withBasicAuth("admin", "admin123")
                .getForEntity(url("/api/orders/admin/stats"), String.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowed.getBody()).contains("order-service");
        assertThat(allowed.getBody()).contains("\"currency\":\"CNY\"");
        assertThat(allowed.getBody()).contains("\"catalogBaseUrlConfigured\":true");
    }

    @Test
    void prometheusEndpointIsPublic() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuator/prometheus"),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm");
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
