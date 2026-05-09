package com.taoking.spring3.order.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.clients.catalog")
public record CatalogClientProperties(
        String baseUrl,
        @NotBlank String username,
        @NotBlank String password,
        CatalogClientMode mode,
        Duration connectTimeout,
        Duration readTimeout
) {
    public CatalogClientProperties {
        mode = mode == null ? CatalogClientMode.FEIGN : mode;
        connectTimeout = connectTimeout == null ? Duration.ofMillis(500) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMillis(800) : readTimeout;
    }

    public boolean hasBaseUrl() {
        return StringUtils.hasText(baseUrl);
    }

    public enum CatalogClientMode {
        FEIGN,
        RESTCLIENT
    }
}
