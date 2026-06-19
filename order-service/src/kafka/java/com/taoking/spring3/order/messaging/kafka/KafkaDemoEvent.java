package com.taoking.spring3.order.messaging.kafka;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record KafkaDemoEvent(
        String eventId,
        String scenario,
        int eventVersion,
        String source,
        Instant occurredAt,
        String partitionKey,
        int sequence,
        long processingDelayMs,
        Map<String, Object> payload
) {

    static final String SOURCE = "order-service";

    static KafkaDemoEvent of(String scenario, String partitionKey, int sequence, Map<String, Object> payload) {
        return of("demo-" + UUID.randomUUID(), scenario, 1, partitionKey, sequence, 0, payload);
    }

    static KafkaDemoEvent of(
            String eventId,
            String scenario,
            int eventVersion,
            String partitionKey,
            int sequence,
            long processingDelayMs,
            Map<String, Object> payload
    ) {
        return new KafkaDemoEvent(
                eventId,
                scenario,
                eventVersion,
                SOURCE,
                Instant.now(),
                partitionKey,
                sequence,
                processingDelayMs,
                payload == null ? Map.of() : Map.copyOf(payload)
        );
    }

    KafkaDemoEvent withScenario(String nextScenario, Map<String, Object> extraPayload) {
        Map<String, Object> merged = new LinkedHashMap<>(payload);
        if (extraPayload != null) {
            merged.putAll(extraPayload);
        }
        return new KafkaDemoEvent(
                eventId,
                nextScenario,
                eventVersion,
                source,
                Instant.now(),
                partitionKey,
                sequence,
                processingDelayMs,
                merged
        );
    }
}
