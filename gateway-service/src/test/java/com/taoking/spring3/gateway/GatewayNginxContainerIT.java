package com.taoking.spring3.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.gateway.rate-limit.enabled=false",
        "management.zipkin.tracing.export.enabled=false"
})
@AutoConfigureWebTestClient
@Testcontainers(disabledWithoutDocker = true)
class GatewayNginxContainerIT {

    private static final DockerImageName NGINX_IMAGE = DockerImageName.parse("nginx:1.27.3-alpine");

    @Container
    static final GenericContainer<?> catalogBackend = new GenericContainer<>(NGINX_IMAGE)
            .withExposedPorts(80)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("testcontainers/nginx-default.conf"),
                    "/etc/nginx/conf.d/default.conf"
            )
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("demo.gateway.routes.catalog-uri", GatewayNginxContainerIT::catalogBackendUrl);
        registry.add("demo.gateway.routes.order-uri", GatewayNginxContainerIT::catalogBackendUrl);
    }

    @Test
    void catalogRouteCanReachContainerizedDownstream() {
        webTestClient.get()
                .uri("/catalog/api/catalog/products/SKU-TC")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjp1c2VyMTIz")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.sku").isEqualTo("SKU-TC")
                .jsonPath("$.name").isEqualTo("Testcontainers Product");
    }

    @Test
    void missingContainerizedDownstreamPathReturnsNotFoundThroughGateway() {
        webTestClient.get()
                .uri("/catalog/api/catalog/products/UNKNOWN")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("not found"));
    }

    private static String catalogBackendUrl() {
        return "http://" + catalogBackend.getHost() + ":" + catalogBackend.getMappedPort(80);
    }
}
