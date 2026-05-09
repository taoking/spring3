package com.taoking.spring3.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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
        "demo.gateway.rate-limit.enabled=true",
        "demo.gateway.rate-limit.requests-per-window=1",
        "demo.gateway.rate-limit.window=1m"
})
@AutoConfigureWebTestClient
class GatewayRateLimitTest {

    private static MockWebServer catalogServer;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startBackend() throws IOException {
        catalogServer = new MockWebServer();
        catalogServer.start();
    }

    @AfterAll
    static void stopBackend() throws IOException {
        catalogServer.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("demo.gateway.routes.catalog-uri", () -> backendUrl(catalogServer));
        registry.add("demo.gateway.routes.order-uri", () -> backendUrl(catalogServer));
    }

    @Test
    void localRateLimitRejectsRequestsAfterWindowQuota() {
        catalogServer.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("[]"));
        catalogServer.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("[]"));

        webTestClient.get()
                .uri("/catalog/api/catalog/products")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjp1c2VyMTIz")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "1")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0");

        webTestClient.get()
                .uri("/catalog/api/catalog/products")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjp1c2VyMTIz")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("X-RateLimit-Limit", "1")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Gateway rate limit exceeded");

        assertThat(catalogServer.getRequestCount()).isEqualTo(1);
    }

    private static String backendUrl(MockWebServer server) {
        return server.url("/").toString().replace("localhost", "127.0.0.1").replaceAll("/$", "");
    }
}
