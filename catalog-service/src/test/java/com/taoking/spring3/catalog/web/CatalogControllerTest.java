package com.taoking.spring3.catalog.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taoking.spring3.common.api.ApiErrorCodes;
import com.taoking.spring3.common.api.ApiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm")));
    }

    @Test
    void businessEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/catalog/products/SKU-1001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReadProduct() throws Exception {
        mockMvc.perform(get("/api/catalog/products/SKU-1001")
                        .with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1001"))
                .andExpect(jsonPath("$.fallback").value(false));
    }

    @Test
    void missingProductUsesProblemDetail() throws Exception {
        mockMvc.perform(get("/api/catalog/products/UNKNOWN")
                        .with(httpBasic("user", "user123"))
                        .header(ApiHeaders.REQUEST_ID, "catalog-missing-request"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(ApiHeaders.REQUEST_ID, "catalog-missing-request"))
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.CATALOG_PRODUCT_NOT_FOUND))
                .andExpect(jsonPath("$.requestId").value("catalog-missing-request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void adminStatsRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/catalog/admin/stats")
                        .with(httpBasic("user", "user123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/catalog/admin/stats")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCount").value(3))
                .andExpect(jsonPath("$.slowDelay").value("PT2S"));
    }

    @Test
    void listProductsReturnsConfiguredProducts() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                        .with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void openApiDocsExposeCatalogGroups() throws Exception {
        mockMvc.perform(get("/v3/api-docs/catalog-public"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/catalog/products")));

        mockMvc.perform(get("/v3/api-docs/catalog-admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/catalog/admin/stats")));
    }

    @Test
    void sentryProbeRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/sentry-error")
                        .with(httpBasic("user", "user123")))
                .andExpect(status().isForbidden());
    }
}
