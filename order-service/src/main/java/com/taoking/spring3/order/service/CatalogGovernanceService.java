package com.taoking.spring3.order.service;

import com.taoking.spring3.common.dto.ProductResponse;
import com.taoking.spring3.order.client.CatalogFallbackSupport;
import com.taoking.spring3.order.config.CatalogGovernanceProperties;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class CatalogGovernanceService {

    private static final String CATALOG_SERVICE = "catalog-service";
    private static final String CATALOG_RATE_LIMIT = "catalog-rate-limit";
    private static final String CATALOG_BULKHEAD = "catalog-bulkhead";

    private final CatalogLookupService catalogLookupService;
    private final CatalogFallbackSupport fallbackSupport;
    private final CatalogGovernanceProperties properties;
    private final ExecutorService catalogGovernanceExecutor;
    private final ObjectProvider<Tracer> tracerProvider;

    public CatalogGovernanceService(
            CatalogLookupService catalogLookupService,
            CatalogFallbackSupport fallbackSupport,
            CatalogGovernanceProperties properties,
            ExecutorService catalogGovernanceExecutor,
            ObjectProvider<Tracer> tracerProvider
    ) {
        this.catalogLookupService = catalogLookupService;
        this.fallbackSupport = fallbackSupport;
        this.properties = properties;
        this.catalogGovernanceExecutor = catalogGovernanceExecutor;
        this.tracerProvider = tracerProvider;
    }

    @Retry(name = CATALOG_SERVICE, fallbackMethod = "fallback")
    @CircuitBreaker(name = CATALOG_SERVICE)
    public ProductResponse getProduct(String sku, boolean slow, boolean fail) {
        return lookup(sku, slow, fail);
    }

    @CircuitBreaker(name = CATALOG_SERVICE, fallbackMethod = "fallbackAsync")
    @TimeLimiter(name = CATALOG_SERVICE, fallbackMethod = "fallbackAsync")
    public CompletionStage<ProductResponse> getProductWithTimeLimiter(String sku, boolean slow, boolean fail) {
        return CompletableFuture.supplyAsync(() -> lookup(sku, slow, fail), traceAwareExecutor());
    }

    @RateLimiter(name = CATALOG_RATE_LIMIT, fallbackMethod = "fallback")
    public ProductResponse getProductWithRateLimit(String sku, boolean slow, boolean fail) {
        return lookup(sku, slow, fail);
    }

    @Bulkhead(name = CATALOG_BULKHEAD, fallbackMethod = "fallback")
    public ProductResponse getProductWithBulkhead(String sku, boolean slow, boolean fail, boolean holdBulkhead) {
        if (holdBulkhead) {
            sleep(properties.bulkheadHoldDuration());
        }
        return lookup(sku, slow, fail);
    }

    private ProductResponse lookup(String sku, boolean slow, boolean fail) {
        ProductResponse product = catalogLookupService.getProduct(sku, slow, fail);
        if (fail && product.fallback()) {
            throw new CatalogLookupFailedException(sku);
        }
        return product;
    }

    private CompletionStage<ProductResponse> fallbackAsync(String sku, boolean slow, boolean fail, Throwable cause) {
        return CompletableFuture.completedFuture(fallbackSupport.fallbackProduct(sku, cause));
    }

    private ProductResponse fallback(String sku, boolean slow, boolean fail, Throwable cause) {
        return fallbackSupport.fallbackProduct(sku, cause);
    }

    private ProductResponse fallback(String sku, boolean slow, boolean fail, boolean holdBulkhead, Throwable cause) {
        return fallbackSupport.fallbackProduct(sku, cause);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Catalog bulkhead hold simulation was interrupted", ex);
        }
    }

    private Executor traceAwareExecutor() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return catalogGovernanceExecutor;
        }
        return tracer.currentTraceContext().wrap(catalogGovernanceExecutor);
    }
}
