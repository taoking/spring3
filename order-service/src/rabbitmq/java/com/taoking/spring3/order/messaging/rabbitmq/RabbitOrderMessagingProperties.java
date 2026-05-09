package com.taoking.spring3.order.messaging.rabbitmq;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "demo.messaging.rabbitmq")
public record RabbitOrderMessagingProperties(
        @NotBlank String exchange,
        @NotBlank String routingKey,
        @NotBlank String queue,
        @NotBlank String deadLetterExchange,
        @NotBlank String deadLetterRoutingKey,
        @NotBlank String deadLetterQueue,
        String poisonSku
) {
}
