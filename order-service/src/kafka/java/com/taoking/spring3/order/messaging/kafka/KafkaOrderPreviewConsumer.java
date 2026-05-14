package com.taoking.spring3.order.messaging.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("kafka")
class KafkaOrderPreviewConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderPreviewConsumer.class);

    private final KafkaOrderMessagingProperties properties;
    private final ProcessedKafkaEventStore processedEventStore;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    KafkaOrderPreviewConsumer(
            KafkaOrderMessagingProperties properties,
            ProcessedKafkaEventStore processedEventStore,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.processedEventStore = processedEventStore;
        this.processedCounter = Counter.builder("orders.preview.kafka.processed.total")
                .description("Number of Kafka order preview events consumed successfully")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("orders.preview.kafka.duplicates.total")
                .description("Number of duplicated Kafka order preview events skipped")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("orders.preview.kafka.failed.total")
                .description("Number of Kafka order preview events rejected by the demo consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${demo.messaging.kafka.topic}",
            groupId = "${demo.messaging.kafka.consumer-group}",
            containerFactory = "kafkaOrderPreviewListenerContainerFactory"
    )
    void onOrderPreviewCreated(
            OrderPreviewKafkaEvent event,
            ConsumerRecord<String, OrderPreviewKafkaEvent> record,
            Acknowledgment acknowledgment
    ) {
        validate(event);
        if (!processedEventStore.markProcessing(event.eventId(), event)) {
            processedEventStore.incrementDuplicateEvents();
            duplicateCounter.increment();
            acknowledgment.acknowledge();
            log.info("Skipped duplicate Kafka order preview event eventId={} requestId={} traceId={} topic={} partition={} offset={} group={}",
                    event.eventId(),
                    event.requestId(),
                    event.traceId(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    properties.consumerGroup());
            return;
        }

        if (properties.poisonSku().equals(event.payload().sku())) {
            processedEventStore.markFailed(event.eventId());
            failedCounter.increment();
            log.warn("Rejected Kafka order preview event eventId={} requestId={} traceId={} sku={} topic={} partition={} offset={} group={}",
                    event.eventId(),
                    event.requestId(),
                    event.traceId(),
                    event.payload().sku(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    properties.consumerGroup());
            throw new IllegalStateException("Simulated Kafka consumer failure for sku=" + event.payload().sku());
        }

        processedCounter.increment();
        acknowledgment.acknowledge();
        log.info("Consumed Kafka order preview event eventId={} requestId={} traceId={} orderId={} sku={} quantity={} topic={} partition={} offset={} group={}",
                event.eventId(),
                event.requestId(),
                event.traceId(),
                event.payload().orderId(),
                event.payload().sku(),
                event.payload().quantity(),
                record.topic(),
                record.partition(),
                record.offset(),
                properties.consumerGroup());
    }

    boolean hasProcessed(String eventId) {
        return processedEventStore.hasProcessed(eventId);
    }

    OrderPreviewKafkaEvent processedEvent(String eventId) {
        return processedEventStore.processedEvent(eventId);
    }

    long processedEventCount() {
        return processedEventStore.processedEventCount();
    }

    long duplicateEventCount() {
        return processedEventStore.duplicateEventCount();
    }

    void resetState() {
        processedEventStore.resetState();
    }

    private void validate(OrderPreviewKafkaEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Kafka order preview event is missing payload");
        }
        if (!StringUtils.hasText(event.eventId())) {
            throw new IllegalArgumentException("Kafka order preview event is missing eventId");
        }
        if (event.payload() == null || !StringUtils.hasText(event.payload().sku())) {
            throw new IllegalArgumentException("Kafka order preview event is missing sku");
        }
    }
}
