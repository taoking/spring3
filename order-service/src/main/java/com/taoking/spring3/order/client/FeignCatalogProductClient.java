package com.taoking.spring3.order.client;

import com.taoking.spring3.common.dto.ProductResponse;
import com.taoking.spring3.order.config.CatalogClientProperties.CatalogClientMode;
import org.springframework.stereotype.Component;

@Component
class FeignCatalogProductClient implements CatalogProductClient {

    private final CatalogClient catalogClient;

    FeignCatalogProductClient(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Override
    public CatalogClientMode mode() {
        return CatalogClientMode.FEIGN;
    }

    @Override
    public ProductResponse getProduct(String sku, boolean slow, boolean fail) {
        return catalogClient.getProduct(sku, slow, fail);
    }
}
