package com.taoking.spring3.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
    private static MockWebServer orderCanaryServer;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private GlobalCorsProperties globalCorsProperties;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startBackends() throws IOException {
        catalogServer = new MockWebServer();
        orderServer = new MockWebServer();
        orderCanaryServer = new MockWebServer();
        catalogServer.start();
        orderServer.start();
        orderCanaryServer.start();
    }

    @AfterAll
    static void stopBackends() throws IOException {
        catalogServer.shutdown();
        orderServer.shutdown();
        orderCanaryServer.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("demo.gateway.routes.catalog-uri", () -> backendUrl(catalogServer));
        registry.add("demo.gateway.routes.order-uri", () -> backendUrl(orderServer));
        registry.add("demo.gateway.routes.order-canary-uri", () -> backendUrl(orderCanaryServer));
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
    void orderCanaryRouteUsesCanaryBackendWhenHeaderMatches() throws Exception {
        orderCanaryServer.enqueue(jsonResponse("""
                {"version":"canary"}
                """));

        webTestClient.get()
                .uri("/orders/api/orders/admin/stats")
                .header("X-Canary", "true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.version").isEqualTo("canary");

        RecordedRequest request = orderCanaryServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/api/orders/admin/stats");
    }

    @Test
    void corsPreflightAllowsConfiguredLocalOrigins() {
        assertThat(globalCorsProperties.getCorsConfigurations()).containsKey("/**");
        var corsConfiguration = globalCorsProperties.getCorsConfigurations().get("/**");
        assertThat(corsConfiguration.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThat(corsConfiguration.checkHttpMethod(HttpMethod.POST)).contains(HttpMethod.POST);
        assertThat(corsConfiguration.checkHeaders(List.of("Authorization", "Content-Type", "X-Request-Id")))
                .contains("Authorization", "Content-Type", "X-Request-Id");

        webTestClient.options()
                .uri("http://localhost:" + port + "/orders/api/orders/preview")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type,X-Request-Id")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        value -> assertThat(value).contains("POST"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> assertThat(value).contains("Authorization"));
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

    private static String backendUrl(MockWebServer server) {
        return server.url("/").toString().replace("localhost", "127.0.0.1").replaceAll("/$", "");
    }
}
