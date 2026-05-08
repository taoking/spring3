package com.taoking.spring3.order.service;

import com.taoking.spring3.common.dto.ProductResponse;
import com.taoking.spring3.order.client.CatalogClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CatalogLookupService {

    private final CatalogClient catalogClient;

    public CatalogLookupService(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Cacheable(cacheNames = "catalogProducts", key = "#sku + ':' + #slow + ':' + #fail", unless = "#result.fallback()")
    public ProductResponse getProduct(String sku, boolean slow, boolean fail) {
        return catalogClient.getProduct(sku, slow, fail);
    }
}
