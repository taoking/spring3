package com.taoking.spring3.order.service;

import com.taoking.spring3.common.aop.DemoLog;
import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import com.taoking.spring3.common.dto.ProductResponse;
import com.taoking.spring3.order.event.OrderPreviewCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final CatalogGovernanceService catalogGovernanceService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Counter previewCounter;
    private final Counter fallbackCounter;

    public OrderService(
            CatalogGovernanceService catalogGovernanceService,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.catalogGovernanceService = catalogGovernanceService;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.previewCounter = Counter.builder("orders.preview.total")
                .description("Number of order preview requests")
                .register(meterRegistry);
        this.fallbackCounter = Counter.builder("orders.preview.fallback.total")
                .description("Number of order previews using catalog fallback")
                .register(meterRegistry);
    }

    @DemoLog("order.preview")
    public OrderPreviewResponse preview(
            OrderPreviewRequest request,
            boolean slowCatalog,
            boolean failCatalog,
            boolean rateLimit,
            boolean bulkhead,
            boolean holdBulkhead
    ) {
        previewCounter.increment();
        ProductResponse product = getProduct(request, slowCatalog, failCatalog, rateLimit, bulkhead, holdBulkhead);
        if (product.fallback()) {
            fallbackCounter.increment();
        }

        BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(request.quantity()));
        String orderId = "preview-" + UUID.randomUUID();
        OrderPreviewResponse response = new OrderPreviewResponse(
                orderId,
                product,
                request.quantity(),
                subtotal,
                product.fallback(),
                product.fallback() ? "Catalog fallback was used" : "Preview calculated"
        );

        eventPublisher.publishEvent(new OrderPreviewCreatedEvent(response, Instant.now()));
        notificationService.sendPreviewNotification(orderId);
        return response;
    }

    private ProductResponse getProduct(
            OrderPreviewRequest request,
            boolean slowCatalog,
            boolean failCatalog,
            boolean rateLimit,
            boolean bulkhead,
            boolean holdBulkhead
    ) {
        if (bulkhead) {
            return catalogGovernanceService.getProductWithBulkhead(
                    request.sku(),
                    slowCatalog,
                    failCatalog,
                    holdBulkhead
            );
        }
        if (rateLimit) {
            return catalogGovernanceService.getProductWithRateLimit(request.sku(), slowCatalog, failCatalog);
        }
        if (slowCatalog) {
            return catalogGovernanceService.getProductWithTimeLimiter(request.sku(), true, failCatalog)
                    .toCompletableFuture()
                    .join();
        }
        return catalogGovernanceService.getProduct(request.sku(), false, failCatalog);
    }
}
