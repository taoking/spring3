package com.taoking.spring3.gateway.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.gateway.rate-limit")
public record LocalRateLimitProperties(
        boolean enabled,
        @Positive int requestsPerWindow,
        @NotNull Duration window
) {
}
