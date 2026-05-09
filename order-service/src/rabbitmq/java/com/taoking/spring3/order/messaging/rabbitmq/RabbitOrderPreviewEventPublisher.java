package com.taoking.spring3.order.messaging.rabbitmq;

import com.taoking.spring3.order.event.OrderPreviewCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Profile("rabbitmq")
class RabbitOrderPreviewEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderPreviewEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitOrderMessagingProperties properties;
    private final Counter publishedCounter;

    RabbitOrderPreviewEventPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitOrderMessagingProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.publishedCounter = Counter.builder("orders.preview.rabbitmq.published.total")
                .description("Number of order preview events published to RabbitMQ")
                .register(meterRegistry);
    }

    @Async("demoTaskExecutor")
    @EventListener
    void onOrderPreviewCreated(OrderPreviewCreatedEvent event) {
        OrderPreviewMessage message = OrderPreviewMessage.from(event);
        rabbitTemplate.convertAndSend(properties.exchange(), properties.routingKey(), message, rabbitMessage -> {
            rabbitMessage.getMessageProperties().setMessageId(message.eventId());
            rabbitMessage.getMessageProperties().setHeader("eventId", message.eventId());
            rabbitMessage.getMessageProperties().setHeader("eventType", "OrderPreviewCreated");
            return rabbitMessage;
        });
        publishedCounter.increment();
        log.info("Published order preview message eventId={} exchange={} routingKey={}",
                message.eventId(),
                properties.exchange(),
                properties.routingKey());
    }
}
