package com.taoking.spring3.catalog.web;

import com.taoking.spring3.catalog.service.CatalogService;
import com.taoking.spring3.common.dto.ProductResponse;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
class CatalogController {

    private final CatalogService catalogService;

    CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    List<ProductResponse> listProducts() {
        return catalogService.listProducts();
    }

    @GetMapping("/products/{sku}")
    ProductResponse getProduct(
            @PathVariable String sku,
            @RequestParam(defaultValue = "false") boolean slow,
            @RequestParam(defaultValue = "false") boolean fail
    ) {
        return catalogService.findBySku(sku, slow, fail);
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Object> stats() {
        return Map.of(
                "service", "catalog-service",
                "productCount", catalogService.productCount()
        );
    }
}
