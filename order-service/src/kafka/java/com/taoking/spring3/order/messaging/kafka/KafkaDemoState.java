package com.taoking.spring3.order.messaging.kafka;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
class KafkaDemoState {

    private static final int MAX_RECENT_RECORDS = 200;

    private final Set<String> consumedEventIds = ConcurrentHashMap.newKeySet();
    private final Map<String, CopyOnWriteArrayList<String>> orderedEventIdsByKey = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryAttemptsByEventId = new ConcurrentHashMap<>();
    private final Set<String> retryDltEventIds = ConcurrentHashMap.newKeySet();
    private final Set<String> schemaAcceptedEventIds = ConcurrentHashMap.newKeySet();
    private final Set<String> transactionInputEventIds = ConcurrentHashMap.newKeySet();
    private final Set<String> transactionAuditEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong duplicateCount = new AtomicLong();
    private final AtomicLong lagProcessedCount = new AtomicLong();
    private final AtomicLong rebalanceEventCount = new AtomicLong();
    private final CopyOnWriteArrayList<Map<String, Object>> recentRecords = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Map<String, Object>> rebalanceEvents = new CopyOnWriteArrayList<>();

    boolean markConsumed(KafkaDemoEvent event, ConsumerRecord<String, KafkaDemoEvent> record) {
        boolean first = consumedEventIds.add(event.eventId());
        if (first) {
            orderedEventIdsByKey.computeIfAbsent(event.partitionKey(), ignored -> new CopyOnWriteArrayList<>())
                    .add(event.eventId());
        } else {
            duplicateCount.incrementAndGet();
        }
        addRecentRecord("consume", event, record.topic(), record.partition(), record.offset(), Map.of("first", first));
        return first;
    }

    void recordLagProcessed(KafkaDemoEvent event, ConsumerRecord<String, KafkaDemoEvent> record) {
        lagProcessedCount.incrementAndGet();
        addRecentRecord("lag", event, record.topic(), record.partition(), record.offset(), Map.of());
    }

    void recordRetryAttempt(KafkaDemoEvent event, int attempt, String topic) {
        retryAttemptsByEventId.computeIfAbsent(event.eventId(), ignored -> new AtomicInteger()).set(attempt);
        addRecentRecord("retry-attempt", event, topic, -1, -1, Map.of("attempt", attempt));
    }

    void recordRetryDlt(KafkaDemoEvent event, ConsumerRecord<String, KafkaDemoEvent> record) {
        retryDltEventIds.add(event.eventId());
        addRecentRecord("retry-dlt", event, record.topic(), record.partition(), record.offset(), Map.of());
    }

    void recordSchemaAccepted(String eventId, int eventVersion, String partitionKey, List<String> ignoredFields) {
        schemaAcceptedEventIds.add(eventId);
        addRecentRecord(
                "schema-v" + eventVersion,
                eventId,
                "schema",
                -1,
                -1,
                Map.of("partitionKey", partitionKey, "ignoredFields", ignoredFields)
        );
    }

    void recordTransactionInput(KafkaDemoEvent event, ConsumerRecord<String, KafkaDemoEvent> record) {
        transactionInputEventIds.add(event.eventId());
        addRecentRecord("transaction-input", event, record.topic(), record.partition(), record.offset(), Map.of());
    }

    void recordTransactionAudit(KafkaDemoEvent event, ConsumerRecord<String, KafkaDemoEvent> record) {
        transactionAuditEventIds.add(event.eventId());
        addRecentRecord("transaction-audit", event, record.topic(), record.partition(), record.offset(), Map.of());
    }

    void recordRebalance(String action, Collection<TopicPartition> partitions) {
        rebalanceEventCount.incrementAndGet();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("time", Instant.now().toString());
        item.put("action", action);
        item.put("partitions", partitions.stream().map(TopicPartition::toString).toList());
        rebalanceEvents.add(item);
        trim(rebalanceEvents);
    }

    void reset() {
        consumedEventIds.clear();
        orderedEventIdsByKey.clear();
        retryAttemptsByEventId.clear();
        retryDltEventIds.clear();
        schemaAcceptedEventIds.clear();
        transactionInputEventIds.clear();
        transactionAuditEventIds.clear();
        duplicateCount.set(0);
        lagProcessedCount.set(0);
        rebalanceEventCount.set(0);
        recentRecords.clear();
        rebalanceEvents.clear();
    }

    boolean hasConsumed(String eventId) {
        return consumedEventIds.contains(eventId);
    }

    boolean hasRetryDlt(String eventId) {
        return retryDltEventIds.contains(eventId);
    }

    boolean hasSchemaAccepted(String eventId) {
        return schemaAcceptedEventIds.contains(eventId);
    }

    boolean hasTransactionInput(String eventId) {
        return transactionInputEventIds.contains(eventId);
    }

    boolean hasTransactionAudit(String eventId) {
        return transactionAuditEventIds.contains(eventId);
    }

    long duplicateCount() {
        return duplicateCount.get();
    }

    long lagProcessedCount() {
        return lagProcessedCount.get();
    }

    List<String> orderedEventIdsForKey(String partitionKey) {
        return List.copyOf(orderedEventIdsByKey.getOrDefault(partitionKey, new CopyOnWriteArrayList<>()));
    }

    int retryAttempts(String eventId) {
        AtomicInteger attempts = retryAttemptsByEventId.get(eventId);
        return attempts == null ? 0 : attempts.get();
    }

    Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("consumedEventCount", consumedEventIds.size());
        snapshot.put("duplicateCount", duplicateCount.get());
        snapshot.put("retryDltEventIds", List.copyOf(retryDltEventIds));
        snapshot.put("schemaAcceptedEventIds", List.copyOf(schemaAcceptedEventIds));
        snapshot.put("transactionInputEventIds", List.copyOf(transactionInputEventIds));
        snapshot.put("transactionAuditEventIds", List.copyOf(transactionAuditEventIds));
        snapshot.put("lagProcessedCount", lagProcessedCount.get());
        snapshot.put("rebalanceEventCount", rebalanceEventCount.get());
        snapshot.put("orderedEventIdsByKey", orderedSnapshot());
        snapshot.put("retryAttemptsByEventId", retrySnapshot());
        snapshot.put("recentRecords", List.copyOf(recentRecords));
        snapshot.put("rebalanceEvents", List.copyOf(rebalanceEvents));
        return snapshot;
    }

    private Map<String, List<String>> orderedSnapshot() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        orderedEventIdsByKey.forEach((key, value) -> snapshot.put(key, List.copyOf(value)));
        return snapshot;
    }

    private Map<String, Integer> retrySnapshot() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        retryAttemptsByEventId.forEach((key, value) -> snapshot.put(key, value.get()));
        return snapshot;
    }

    private void addRecentRecord(
            String action,
            KafkaDemoEvent event,
            String topic,
            int partition,
            long offset,
            Map<String, Object> extra
    ) {
        addRecentRecord(action, event.eventId(), topic, partition, offset, merge(Map.of(
                "scenario", event.scenario(),
                "partitionKey", event.partitionKey(),
                "sequence", event.sequence()
        ), extra));
    }

    private void addRecentRecord(
            String action,
            String eventId,
            String topic,
            int partition,
            long offset,
            Map<String, Object> extra
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("time", Instant.now().toString());
        item.put("action", action);
        item.put("eventId", eventId);
        item.put("topic", topic);
        item.put("partition", partition);
        item.put("offset", offset);
        item.putAll(extra);
        recentRecords.add(item);
        trim(recentRecords);
    }

    private Map<String, Object> merge(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> merged = new LinkedHashMap<>(left);
        merged.putAll(right);
        return merged;
    }

    private void trim(CopyOnWriteArrayList<Map<String, Object>> records) {
        if (records.size() <= MAX_RECENT_RECORDS) {
            return;
        }
        List<Map<String, Object>> copy = new ArrayList<>(records);
        records.clear();
        records.addAll(copy.subList(Math.max(0, copy.size() - MAX_RECENT_RECORDS), copy.size()));
    }
}
