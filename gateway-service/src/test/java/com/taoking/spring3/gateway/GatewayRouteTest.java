package com.taoking.spring3.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.gateway.rate-limit.enabled=false"
})
@AutoConfigureWebTestClient
class GatewayRouteTest {

    private static MockWebServer catalogServer;
    private static MockWebServer orderServer;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startBackends() throws IOException {
        catalogServer = new MockWebServer();
        orderServer = new MockWebServer();
        catalogServer.start();
        orderServer.start();
    }

    @AfterAll
    static void stopBackends() throws IOException {
        catalogServer.shutdown();
        orderServer.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("demo.gateway.routes.catalog-uri", () -> catalogServer.url("/").toString().replaceAll("/$", ""));
        registry.add("demo.gateway.routes.order-uri", () -> orderServer.url("/").toString().replaceAll("/$", ""));
    }

    @Test
    void catalogRouteStripsPrefixAddsRequestIdAndRelaysAuthorization() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":1,"sku":"SKU-1001","name":"Spring Boot 3 Guide","price":99.00,"active":true,"fallback":false}
                """));

        webTestClient.get()
                .uri("/catalog/api/catalog/products/SKU-1001")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjp1c2VyMTIz")
                .header("X-Request-Id", "test-request-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "test-request-1")
                .expectBody()
                .jsonPath("$.sku").isEqualTo("SKU-1001");

        RecordedRequest request = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/api/catalog/products/SKU-1001");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Basic dXNlcjp1c2VyMTIz");
        assertThat(request.getHeader("X-Gateway-Auth-Type")).isEqualTo("Basic");
        assertThat(request.getHeader("X-Request-Id")).isEqualTo("test-request-1");
    }

    @Test
    void protectedDownstreamEndpointCanReturnUnauthorizedThroughGateway() throws Exception {
        catalogServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"catalog\""));

        webTestClient.get()
                .uri("/catalog/api/catalog/products/SKU-1001")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id");

        RecordedRequest request = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/api/catalog/products/SKU-1001");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void orderRouteUsesFallbackWhenDownstreamConnectionFails() {
        orderServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        webTestClient.post()
                .uri("/orders/api/orders/preview")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjp1c2VyMTIz")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sku":"SKU-1001","quantity":2}
                        """)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Gateway fallback")
                .jsonPath("$.service").isEqualTo("orders");
    }

    @Test
    void actuatorHealthAndPrometheusArePublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("jvm"));
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }
}
