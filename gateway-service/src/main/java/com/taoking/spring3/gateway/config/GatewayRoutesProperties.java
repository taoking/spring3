package com.taoking.spring3.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.gateway.routes")
public record GatewayRoutesProperties(
        @NotBlank String catalogUri,
        @NotBlank String orderUri,
        String orderCanaryUri
) {

    public GatewayRoutesProperties {
        orderCanaryUri = StringUtils.hasText(orderCanaryUri) ? orderCanaryUri : orderUri;
    }
}
