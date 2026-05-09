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
import org.springframework.beans.factory.annotation.Autowired;
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
        "demo.clients.catalog.mode=restclient",
        "demo.clients.catalog.connect-timeout=100ms",
        "demo.clients.catalog.read-timeout=100ms",
        "management.zipkin.tracing.export.enabled=false"
})
class OrderRestClientModeTest {

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
    void restClientModeReturnsOrderPreviewAndRelaysBasicAuth() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":101,"sku":"SKU-RC-1","name":"RestClient Guide","price":39.00,"active":true,"fallback":false}
                """));

        ResponseEntity<OrderPreviewResponse> response = restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(
                        url("/api/orders/preview"),
                        new OrderPreviewRequest("SKU-RC-1", 3),
                        OrderPreviewResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().product().sku()).isEqualTo("SKU-RC-1");
        assertThat(response.getBody().quantity()).isEqualTo(3);
        assertThat(response.getBody().fallbackUsed()).isFalse();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-RC-1?slow=false&fail=false");
        assertThat(catalogRequest.getHeader(HttpHeaders.AUTHORIZATION)).startsWith("Basic ");
    }

    @Test
    void restClientModeUsesFallbackOnServerError() throws Exception {
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
                        new OrderPreviewRequest("SKU-RC-500", 2),
                        OrderPreviewResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().product().sku()).isEqualTo("SKU-RC-500");
        assertThat(response.getBody().fallbackUsed()).isTrue();
        assertThat(response.getBody().product().fallback()).isTrue();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-RC-500?slow=false&fail=true");
    }

    @Test
    void restClientModeUsesFallbackOnReadTimeout() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":102,"sku":"SKU-RC-SLOW","name":"Slow Catalog","price":49.00,"active":true,"fallback":false}
                """).setHeadersDelay(300, TimeUnit.MILLISECONDS));

        ResponseEntity<OrderPreviewResponse> response = restTemplate
                .withBasicAuth("user", "user123")
                .postForEntity(
                        url("/api/orders/preview"),
                        new OrderPreviewRequest("SKU-RC-SLOW", 1),
                        OrderPreviewResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().product().sku()).isEqualTo("SKU-RC-SLOW");
        assertThat(response.getBody().fallbackUsed()).isTrue();
        assertThat(response.getBody().product().fallback()).isTrue();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getPath()).isEqualTo("/api/catalog/products/SKU-RC-SLOW?slow=false&fail=false");
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
