package com.taoking.spring3.catalog.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.catalog")
public record CatalogProperties(
        @NotNull Duration slowDelay,
        @NotEmpty List<@Valid CatalogItem> products
) {
    public record CatalogItem(
            @NotNull Long id,
            @NotBlank String sku,
            @NotBlank String name,
            @NotNull @Positive BigDecimal price,
            boolean active
    ) {
    }
}
