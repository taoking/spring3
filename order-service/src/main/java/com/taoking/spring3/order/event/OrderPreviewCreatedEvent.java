package com.taoking.spring3.order.event;

import com.taoking.spring3.common.dto.OrderPreviewResponse;
import java.time.Instant;

public record OrderPreviewCreatedEvent(
        OrderPreviewResponse preview,
        Instant createdAt
) {
}
