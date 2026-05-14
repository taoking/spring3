package com.taoking.spring3.order.messaging.kafka;

interface ProcessedKafkaEventStore {

    boolean markProcessing(String eventId, OrderPreviewKafkaEvent event);

    void markFailed(String eventId);

    boolean hasProcessed(String eventId);

    OrderPreviewKafkaEvent processedEvent(String eventId);

    long processedEventCount();

    long duplicateEventCount();

    void incrementDuplicateEvents();

    void resetState();
}
