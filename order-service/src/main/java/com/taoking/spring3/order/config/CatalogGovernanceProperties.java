package com.taoking.spring3.order.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.resilience.catalog")
public record CatalogGovernanceProperties(
        @Min(1) int asyncPoolSize,
        @NotNull Duration bulkheadHoldDuration
) {
    public CatalogGovernanceProperties {
        asyncPoolSize = asyncPoolSize < 1 ? 4 : asyncPoolSize;
        bulkheadHoldDuration = bulkheadHoldDuration == null ? Duration.ofSeconds(1) : bulkheadHoldDuration;
    }
}
