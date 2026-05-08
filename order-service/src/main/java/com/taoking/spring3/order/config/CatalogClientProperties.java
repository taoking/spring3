package com.taoking.spring3.order.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.clients.catalog")
public record CatalogClientProperties(
        @NotBlank String baseUrl,
        @NotBlank String username,
        @NotBlank String password
) {
}
