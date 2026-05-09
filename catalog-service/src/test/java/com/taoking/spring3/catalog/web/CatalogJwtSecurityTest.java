package com.taoking.spring3.catalog.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jwt")
class CatalogJwtSecurityTest {

    private static final String JWT_SECRET = "spring3-local-dev-secret-key-32-bytes-minimum";
    private static final String WRONG_SECRET = "spring3-local-wrong-secret-key-32-bytes-minimum";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublicInJwtMode() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void businessEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/catalog/products/SKU-1001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/catalog/products/SKU-1001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("user", List.of("USER"), WRONG_SECRET))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenCanReadProduct() throws Exception {
        mockMvc.perform(get("/api/catalog/products/SKU-1001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("user", List.of("USER"), JWT_SECRET))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1001"));
    }

    @Test
    void basicAuthStillWorksForServiceCallsInJwtMode() throws Exception {
        mockMvc.perform(get("/api/catalog/products/SKU-1001")
                        .with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1001"));
    }

    @Test
    void userTokenCannotReadAdminStats() throws Exception {
        mockMvc.perform(get("/api/catalog/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("user", List.of("USER"), JWT_SECRET))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenCanReadAdminStats() throws Exception {
        mockMvc.perform(get("/api/catalog/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("admin", List.of("USER", "ADMIN"), JWT_SECRET))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("productCount")));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String token(String subject, List<String> roles, String secret) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("spring3-local")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("roles", roles)
                .claim("scope", "catalog:read")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
