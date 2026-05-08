package com.taoking.spring3.order.client;

import com.taoking.spring3.common.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "catalog-service",
        url = "${demo.clients.catalog.base-url}",
        fallbackFactory = CatalogClientFallbackFactory.class
)
public interface CatalogClient {

    @GetMapping("/api/catalog/products/{sku}")
    ProductResponse getProduct(
            @PathVariable("sku") String sku,
            @RequestParam(name = "slow", defaultValue = "false") boolean slow,
            @RequestParam(name = "fail", defaultValue = "false") boolean fail
    );
}
