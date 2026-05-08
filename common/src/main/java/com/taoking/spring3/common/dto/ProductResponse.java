package com.taoking.spring3.common.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        BigDecimal price,
        boolean active,
        boolean fallback
) {
}
