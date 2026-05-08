package com.taoking.spring3.catalog.service;

import com.taoking.spring3.catalog.config.CatalogProperties;
import com.taoking.spring3.catalog.domain.ProductNotFoundException;
import com.taoking.spring3.catalog.domain.SimulatedCatalogException;
import com.taoking.spring3.common.aop.DemoLog;
import com.taoking.spring3.common.dto.ProductResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final CatalogProperties properties;
    private final Map<String, CatalogProperties.CatalogItem> productsBySku;
    private final Counter productLookupCounter;
    private final Counter simulatedFailureCounter;

    public CatalogService(CatalogProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.productsBySku = properties.products().stream()
                .collect(Collectors.toUnmodifiableMap(CatalogProperties.CatalogItem::sku, Function.identity()));
        this.productLookupCounter = Counter.builder("catalog.product.lookup")
                .description("Number of catalog product lookups")
                .register(meterRegistry);
        this.simulatedFailureCounter = Counter.builder("catalog.product.simulated_failure")
                .description("Number of simulated catalog failures")
                .register(meterRegistry);
    }

    public List<ProductResponse> listProducts() {
        return productsBySku.values().stream()
                .sorted(Comparator.comparing(CatalogProperties.CatalogItem::id))
                .map(this::toResponse)
                .toList();
    }

    @DemoLog("catalog.find-product")
    public ProductResponse findBySku(String sku, boolean slow, boolean fail) {
        productLookupCounter.increment();
        if (fail) {
            simulatedFailureCounter.increment();
            throw new SimulatedCatalogException(sku);
        }
        if (slow) {
            sleep();
        }
        return productsBySku.values().stream()
                .filter(product -> product.sku().equalsIgnoreCase(sku))
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new ProductNotFoundException(sku));
    }

    public int productCount() {
        return productsBySku.size();
    }

    private ProductResponse toResponse(CatalogProperties.CatalogItem product) {
        return new ProductResponse(
                product.id(),
                product.sku(),
                product.name(),
                product.price(),
                product.active(),
                false
        );
    }

    private void sleep() {
        try {
            Thread.sleep(properties.slowDelay().toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Catalog slow simulation was interrupted", ex);
        }
    }
}
