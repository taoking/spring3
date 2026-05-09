package com.taoking.spring3.order.messaging.rabbitmq;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("rabbitmq")
class RabbitOrderPreviewConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderPreviewConsumer.class);

    private final RabbitOrderMessagingProperties properties;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong duplicateEvents = new AtomicLong();
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    RabbitOrderPreviewConsumer(RabbitOrderMessagingProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.processedCounter = Counter.builder("orders.preview.rabbitmq.processed.total")
                .description("Number of RabbitMQ order preview events consumed successfully")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("orders.preview.rabbitmq.duplicates.total")
                .description("Number of duplicated RabbitMQ order preview events skipped")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("orders.preview.rabbitmq.failed.total")
                .description("Number of RabbitMQ order preview events rejected by the demo consumer")
                .register(meterRegistry);
    }

    @RabbitListener(queues = "${demo.messaging.rabbitmq.queue}")
    void onOrderPreviewCreated(OrderPreviewMessage message) {
        if (!StringUtils.hasText(message.eventId())) {
            throw new IllegalArgumentException("RabbitMQ order preview message is missing eventId");
        }
        if (!processedEventIds.add(message.eventId())) {
            duplicateEvents.incrementAndGet();
            duplicateCounter.increment();
            log.info("Skipped duplicate order preview message eventId={} orderId={}",
                    message.eventId(), message.orderId());
            return;
        }

        if (properties.poisonSku() != null && properties.poisonSku().equals(message.sku())) {
            processedEventIds.remove(message.eventId());
            failedCounter.increment();
            throw new IllegalStateException("Simulated RabbitMQ consumer failure for sku=" + message.sku());
        }

        processedCounter.increment();
        log.info("Consumed order preview message eventId={} orderId={} sku={} quantity={} fallbackUsed={}",
                message.eventId(),
                message.orderId(),
                message.sku(),
                message.quantity(),
                message.fallbackUsed());
    }

    boolean hasProcessed(String eventId) {
        return processedEventIds.contains(eventId);
    }

    long processedEventCount() {
        return processedEventIds.size();
    }

    long duplicateEventCount() {
        return duplicateEvents.get();
    }

    void resetState() {
        processedEventIds.clear();
        duplicateEvents.set(0);
    }
}
