package com.taoking.spring3.order.client;

import com.taoking.spring3.common.dto.ProductResponse;
import com.taoking.spring3.order.config.CatalogClientProperties.CatalogClientMode;

public interface CatalogProductClient {

    CatalogClientMode mode();

    ProductResponse getProduct(String sku, boolean slow, boolean fail);
}
