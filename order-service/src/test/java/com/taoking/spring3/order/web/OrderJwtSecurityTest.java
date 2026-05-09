package com.taoking.spring3.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.order.heartbeat-delay=PT1H",
        "management.zipkin.tracing.export.enabled=false"
})
@ActiveProfiles("jwt")
class OrderJwtSecurityTest {

    private static final String JWT_SECRET = "spring3-local-dev-secret-key-32-bytes-minimum";
    private static final String WRONG_SECRET = "spring3-local-wrong-secret-key-32-bytes-minimum";

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
    void previewRequiresBearerToken() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/orders/preview"),
                new OrderPreviewRequest("SKU-JWT-1", 1),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        HttpHeaders headers = bearerHeaders(token("user", List.of("USER"), WRONG_SECRET));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/orders/preview"),
                HttpMethod.POST,
                new HttpEntity<>(new OrderPreviewRequest("SKU-JWT-INVALID", 1), headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userTokenCanPreviewAndCatalogStillUsesBasicAuth() throws Exception {
        catalogServer.enqueue(jsonResponse("""
                {"id":201,"sku":"SKU-JWT-2","name":"JWT Guide","price":59.00,"active":true,"fallback":false}
                """));

        HttpHeaders headers = bearerHeaders(token("user", List.of("USER"), JWT_SECRET));
        ResponseEntity<OrderPreviewResponse> response = restTemplate.exchange(
                url("/api/orders/preview"),
                HttpMethod.POST,
                new HttpEntity<>(new OrderPreviewRequest("SKU-JWT-2", 2), headers),
                OrderPreviewResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().product().sku()).isEqualTo("SKU-JWT-2");
        assertThat(response.getBody().fallbackUsed()).isFalse();
        RecordedRequest catalogRequest = catalogServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(catalogRequest).isNotNull();
        assertThat(catalogRequest.getHeader(HttpHeaders.AUTHORIZATION)).startsWith("Basic ");
    }

    @Test
    void userTokenCannotReadAdminStats() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/orders/admin/stats"),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token("user", List.of("USER"), JWT_SECRET))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminTokenCanReadAdminStats() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/orders/admin/stats"),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token("admin", List.of("USER", "ADMIN"), JWT_SECRET))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"catalogClientMode\":\"FEIGN\"");
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private String token(String subject, List<String> roles, String secret) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("spring3-local")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("roles", roles)
                .claim("scope", "orders:read")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
