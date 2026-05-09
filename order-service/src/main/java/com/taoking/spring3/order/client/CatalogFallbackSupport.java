package com.taoking.spring3.order.client;

import com.taoking.spring3.common.dto.ProductResponse;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CatalogFallbackSupport {

    private static final Logger log = LoggerFactory.getLogger(CatalogFallbackSupport.class);

    public ProductResponse fallbackProduct(String sku, Throwable cause) {
        log.warn("Using catalog fallback for sku={} cause={}", sku, cause.toString());
        return new ProductResponse(
                -1L,
                sku,
                "Fallback product for " + sku,
                BigDecimal.ZERO,
                false,
                true
        );
    }
}
