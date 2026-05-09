package com.taoking.spring3.order.messaging.rabbitmq;

import com.taoking.spring3.order.event.OrderPreviewCreatedEvent;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderPreviewMessage(
        String eventId,
        String orderId,
        String sku,
        int quantity,
        BigDecimal subtotal,
        boolean fallbackUsed,
        Instant createdAt
) {

    static OrderPreviewMessage from(OrderPreviewCreatedEvent event) {
        return new OrderPreviewMessage(
                event.preview().orderId(),
                event.preview().orderId(),
                event.preview().product().sku(),
                event.preview().quantity(),
                event.preview().subtotal(),
                event.preview().fallbackUsed(),
                event.createdAt()
        );
    }
}
