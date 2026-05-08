package com.taoking.spring3.common.dto;

import java.math.BigDecimal;

public record OrderPreviewResponse(
        String orderId,
        ProductResponse product,
        int quantity,
        BigDecimal subtotal,
        boolean fallbackUsed,
        String message
) {
}
