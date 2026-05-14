package com.taoking.spring3.order.messaging.kafka;

import com.taoking.spring3.order.event.OrderPreviewCreatedEvent;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderPreviewKafkaEvent(
        String eventId,
        String eventType,
        int eventVersion,
        String source,
        Instant occurredAt,
        String aggregateType,
        String aggregateId,
        String partitionKey,
        String requestId,
        String traceId,
        Payload payload
) {

    static final String EVENT_TYPE = "OrderPreviewCreated";
    static final int EVENT_VERSION = 1;
    static final String SOURCE = "order-service";
    static final String AGGREGATE_TYPE = "ORDER_PREVIEW";

    static OrderPreviewKafkaEvent from(OrderPreviewCreatedEvent event, String requestId, String traceId) {
        String orderId = event.preview().orderId();
        return new OrderPreviewKafkaEvent(
                orderId,
                EVENT_TYPE,
                EVENT_VERSION,
                SOURCE,
                event.createdAt(),
                AGGREGATE_TYPE,
                orderId,
                orderId,
                requestId,
                traceId,
                new Payload(
                        orderId,
                        event.preview().product().sku(),
                        event.preview().quantity(),
                        event.preview().subtotal(),
                        event.preview().fallbackUsed()
                )
        );
    }

    public record Payload(
            String orderId,
            String sku,
            int quantity,
            BigDecimal subtotal,
            boolean fallbackUsed
    ) {
    }
}
