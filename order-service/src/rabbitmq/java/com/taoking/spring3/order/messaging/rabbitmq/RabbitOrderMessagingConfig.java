package com.taoking.spring3.order.messaging.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableRabbit
@Profile("rabbitmq")
@EnableConfigurationProperties(RabbitOrderMessagingProperties.class)
class RabbitOrderMessagingConfig {

    @Bean
    DirectExchange orderPreviewExchange(RabbitOrderMessagingProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    DirectExchange orderPreviewDeadLetterExchange(RabbitOrderMessagingProperties properties) {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    Queue orderPreviewQueue(RabbitOrderMessagingProperties properties) {
        return QueueBuilder.durable(properties.queue())
                .withArgument("x-dead-letter-exchange", properties.deadLetterExchange())
                .withArgument("x-dead-letter-routing-key", properties.deadLetterRoutingKey())
                .build();
    }

    @Bean
    Queue orderPreviewDeadLetterQueue(RabbitOrderMessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    Binding orderPreviewBinding(
            Queue orderPreviewQueue,
            DirectExchange orderPreviewExchange,
            RabbitOrderMessagingProperties properties
    ) {
        return BindingBuilder.bind(orderPreviewQueue)
                .to(orderPreviewExchange)
                .with(properties.routingKey());
    }

    @Bean
    Binding orderPreviewDeadLetterBinding(
            Queue orderPreviewDeadLetterQueue,
            DirectExchange orderPreviewDeadLetterExchange,
            RabbitOrderMessagingProperties properties
    ) {
        return BindingBuilder.bind(orderPreviewDeadLetterQueue)
                .to(orderPreviewDeadLetterExchange)
                .with(properties.deadLetterRoutingKey());
    }

    @Bean
    Jackson2JsonMessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
