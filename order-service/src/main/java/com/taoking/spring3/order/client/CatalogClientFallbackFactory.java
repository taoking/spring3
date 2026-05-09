package com.taoking.spring3.order.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
class CatalogClientFallbackFactory implements FallbackFactory<CatalogClient> {

    private final CatalogFallbackSupport fallbackSupport;

    CatalogClientFallbackFactory(CatalogFallbackSupport fallbackSupport) {
        this.fallbackSupport = fallbackSupport;
    }

    @Override
    public CatalogClient create(Throwable cause) {
        return (sku, slow, fail) -> fallbackSupport.fallbackProduct(sku, cause);
    }
}
