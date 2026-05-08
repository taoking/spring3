package com.taoking.spring3.order.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.order")
public record OrderProperties(
        @NotBlank String currency,
        Duration notificationDelay,
        Duration heartbeatDelay
) {
}
