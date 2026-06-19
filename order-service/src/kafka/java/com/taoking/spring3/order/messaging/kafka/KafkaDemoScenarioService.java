package com.taoking.spring3.order.messaging.kafka;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("kafka")
class KafkaDemoScenarioService {

    private final KafkaDemoProperties properties;
    private final KafkaDemoPublisher publisher;
    private final KafkaDemoState state;

    KafkaDemoScenarioService(
            KafkaDemoProperties properties,
            KafkaDemoPublisher publisher,
            KafkaDemoState state
    ) {
        this.properties = properties;
        this.publisher = publisher;
        this.state = state;
    }

    KafkaDemoEvent publishBasic(String key) {
        KafkaDemoEvent event = KafkaDemoEvent.of(
                "BASIC_MODEL",
                key,
                1,
                Map.of("description", "topic/partition/offset/consumer group basic demo")
        );
        publisher.send(properties.topic(), event);
        return event;
    }

    List<KafkaDemoEvent> publishDuplicate(String eventId, String key) {
        KafkaDemoEvent event = KafkaDemoEvent.of(
                eventId,
                "DUPLICATE_IDEMPOTENT",
                1,
                key,
                1,
                0,
                Map.of("description", "same eventId is published twice to demonstrate idempotent consume")
        );
        publisher.send(properties.topic(), event);
        publisher.send(properties.topic(), event);
        return List.of(event, event);
    }

    List<KafkaDemoEvent> publishOrdered(String key, int count) {
        int safeCount = Math.max(1, Math.min(count, 20));
        List<KafkaDemoEvent> events = new ArrayList<>();
        for (int i = 1; i <= safeCount; i++) {
            KafkaDemoEvent event = KafkaDemoEvent.of(
                    "ORDERED_KEY",
                    key,
                    i,
                    Map.of("description", "same key events stay in one partition", "expectedOrder", i)
            );
            publisher.send(properties.topic(), event);
            events.add(event);
        }
        return events;
    }

    KafkaDemoEvent publishRetryTopic(String key, int failUntilAttempt) {
        KafkaDemoEvent event = KafkaDemoEvent.of(
                "RETRY_TOPIC",
                key,
                1,
                Map.of(
                        "description", "manual retry topic demo",
                        "failUntilAttempt", Math.max(1, failUntilAttempt)
                )
        );
        publisher.sendWithRetryAttempt(properties.retryInputTopic(), event, 0, properties.retryInputTopic());
        return event;
    }

    List<KafkaDemoEvent> publishLag(String key, int count, long processingDelayMs) {
        int safeCount = Math.max(1, Math.min(count, 200));
        long safeDelay = Math.max(0, Math.min(processingDelayMs, properties.maxProcessingDelay().toMillis()));
        List<KafkaDemoEvent> events = new ArrayList<>();
        for (int i = 1; i <= safeCount; i++) {
            KafkaDemoEvent event = KafkaDemoEvent.of(
                    "LAG_AND_REBALANCE",
                    key,
                    i,
                    Map.of("description", "slow consumer demo", "processingDelayMs", safeDelay)
            );
            event = new KafkaDemoEvent(
                    event.eventId(),
                    event.scenario(),
                    event.eventVersion(),
                    event.source(),
                    event.occurredAt(),
                    event.partitionKey(),
                    event.sequence(),
                    safeDelay,
                    event.payload()
            );
            publisher.send(properties.lagTopic(), event);
            events.add(event);
        }
        return events;
    }

    String publishSchemaV2(String key) {
        String eventId = "schema-" + UUID.randomUUID();
        publisher.sendSchemaV2(properties.schemaTopic(), eventId, key);
        return eventId;
    }

    KafkaDemoEvent publishCommittedTransaction(String key) {
        KafkaDemoEvent event = KafkaDemoEvent.of(
                "TRANSACTION_INPUT_COMMITTED",
                key,
                1,
                Map.of("description", "committed Kafka transaction is visible to read_committed consumers")
        );
        publisher.sendCommittedTransaction(properties.transactionInputTopic(), event);
        return event;
    }

    KafkaDemoEvent publishAbortedTransaction(String key) {
        KafkaDemoEvent event = KafkaDemoEvent.of(
                "TRANSACTION_INPUT_ABORTED",
                key,
                1,
                Map.of("description", "aborted Kafka transaction is hidden from read_committed consumers")
        );
        publisher.sendAbortedTransaction(properties.transactionInputTopic(), event);
        return event;
    }

    Map<String, Object> securityTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("purpose", "configuration template only; do not commit real secrets");
        template.put("spring.kafka.properties.security.protocol", "SASL_SSL");
        template.put("spring.kafka.properties.sasl.mechanism", "SCRAM-SHA-512");
        template.put("spring.kafka.properties.sasl.jaas.config", "${KAFKA_SASL_JAAS_CONFIG}");
        template.put("aclExamples", List.of(
                "allow Write on topic " + properties.topic() + " for order-service",
                "allow Read on topic " + properties.topic() + " for group " + properties.consumerGroup(),
                "allow Read on topic " + properties.retryDltTopic() + " only for operators"
        ));
        return template;
    }

    Map<String, Object> capacityPlan(int peakMessagesPerSecond, int consumerMessageCostMs, int targetPartitionThroughput) {
        int safePeak = Math.max(1, peakMessagesPerSecond);
        int safeCost = Math.max(1, consumerMessageCostMs);
        int safePartitionThroughput = Math.max(1, targetPartitionThroughput);
        int producerPartitions = (int) Math.ceil(safePeak / (double) safePartitionThroughput);
        int consumerPartitions = (int) Math.ceil((safePeak * safeCost) / 1000.0);
        int recommended = Math.max(1, Math.max(producerPartitions, consumerPartitions));
        return Map.of(
                "peakMessagesPerSecond", safePeak,
                "consumerMessageCostMs", safeCost,
                "targetPartitionThroughput", safePartitionThroughput,
                "producerThroughputPartitions", producerPartitions,
                "consumerConcurrencyPartitions", consumerPartitions,
                "recommendedPartitions", recommended,
                "warning", "partition count must also consider key distribution, ordering, future expansion, broker resources"
        );
    }

    Map<String, Object> selectionMatrix() {
        return Map.of(
                "Kafka", List.of("event stream", "high throughput", "replay", "consumer lag and offset semantics"),
                "RabbitMQ", List.of("exchange/queue routing", "traditional task queue", "simple DLQ model"),
                "RocketMQ", List.of("transaction message", "delay message", "ordered business message"),
                "currentProject", "Kafka and RabbitMQ are optional profiles; real-time order preview still uses synchronous HTTP"
        );
    }

    Map<String, Object> state() {
        return state.snapshot();
    }

    void reset() {
        state.reset();
    }
}
