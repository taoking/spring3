package com.taoking.spring3.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.gateway.routes")
public record GatewayRoutesProperties(
        @NotBlank String catalogUri,
        @NotBlank String orderUri
) {
}
