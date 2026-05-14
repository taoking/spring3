package com.taoking.spring3.order.messaging.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
class InMemoryProcessedKafkaEventStore implements ProcessedKafkaEventStore {

    private final Map<String, OrderPreviewKafkaEvent> processedEvents = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<String>> processedEventIdsByKey = new ConcurrentHashMap<>();
    private final AtomicLong duplicateEvents = new AtomicLong();

    @Override
    public boolean markProcessing(String eventId, OrderPreviewKafkaEvent event) {
        OrderPreviewKafkaEvent previous = processedEvents.putIfAbsent(eventId, event);
        if (previous == null) {
            processedEventIdsByKey
                    .computeIfAbsent(event.partitionKey(), ignored -> new CopyOnWriteArrayList<>())
                    .add(eventId);
            return true;
        }
        return false;
    }

    @Override
    public void markFailed(String eventId) {
        OrderPreviewKafkaEvent removed = processedEvents.remove(eventId);
        if (removed != null) {
            List<String> eventIds = processedEventIdsByKey.get(removed.partitionKey());
            if (eventIds != null) {
                eventIds.remove(eventId);
            }
        }
    }

    @Override
    public boolean hasProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    @Override
    public OrderPreviewKafkaEvent processedEvent(String eventId) {
        return processedEvents.get(eventId);
    }

    @Override
    public long processedEventCount() {
        return processedEvents.size();
    }

    @Override
    public long duplicateEventCount() {
        return duplicateEvents.get();
    }

    @Override
    public void incrementDuplicateEvents() {
        duplicateEvents.incrementAndGet();
    }

    List<String> processedEventIdsForKey(String key) {
        return new ArrayList<>(processedEventIdsByKey.getOrDefault(key, new CopyOnWriteArrayList<>()));
    }

    @Override
    public void resetState() {
        processedEvents.clear();
        processedEventIdsByKey.clear();
        duplicateEvents.set(0);
    }
}
