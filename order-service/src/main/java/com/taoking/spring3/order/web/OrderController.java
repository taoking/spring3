package com.taoking.spring3.order.web;

import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import com.taoking.spring3.order.config.CatalogClientProperties;
import com.taoking.spring3.order.config.OrderProperties;
import com.taoking.spring3.order.service.OrderService;
import com.taoking.spring3.order.service.ThreadProbeResponse;
import com.taoking.spring3.order.service.ThreadProbeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
class OrderController {

    private final OrderService orderService;
    private final ThreadProbeService threadProbeService;
    private final OrderProperties orderProperties;
    private final CatalogClientProperties catalogClientProperties;

    OrderController(
            OrderService orderService,
            ThreadProbeService threadProbeService,
            OrderProperties orderProperties,
            CatalogClientProperties catalogClientProperties
    ) {
        this.orderService = orderService;
        this.threadProbeService = threadProbeService;
        this.orderProperties = orderProperties;
        this.catalogClientProperties = catalogClientProperties;
    }

    @PostMapping("/preview")
    OrderPreviewResponse preview(
            @Valid @RequestBody OrderPreviewRequest request,
            @RequestParam(defaultValue = "false") boolean slowCatalog,
            @RequestParam(defaultValue = "false") boolean failCatalog,
            @RequestParam(defaultValue = "false") boolean rateLimit,
            @RequestParam(defaultValue = "false") boolean bulkhead,
            @RequestParam(defaultValue = "false") boolean holdBulkhead,
            @RequestParam(defaultValue = "false") boolean sentinelFlow,
            @RequestParam(defaultValue = "false") boolean sentinelHotSku
    ) {
        return orderService.preview(
                request,
                slowCatalog,
                failCatalog,
                rateLimit,
                bulkhead,
                holdBulkhead,
                sentinelFlow,
                sentinelHotSku
        );
    }

    @GetMapping("/thread-probe")
    ThreadProbeResponse threadProbe(
            @RequestParam(defaultValue = "100") @Min(0) @Max(5000) long delayMs,
            @RequestParam(defaultValue = "false") boolean async
    ) {
        if (async) {
            return threadProbeService.waitOnAsyncExecutor(delayMs).join();
        }
        return threadProbeService.waitOnRequestThread(delayMs);
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Object> stats() {
        return Map.of(
                "service", "order-service",
                "status", "ready",
                "currency", orderProperties.currency(),
                "catalogClientMode", catalogClientProperties.mode(),
                "catalogBaseUrlConfigured", catalogClientProperties.hasBaseUrl()
        );
    }

    @PostMapping("/admin/sentry-error")
    @PreAuthorize("hasRole('ADMIN')")
    void triggerSentryError() {
        throw new IllegalStateException("Sentry probe from order-service");
    }
}
