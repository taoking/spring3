package com.taoking.spring3.order.web;

import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import com.taoking.spring3.order.config.CatalogClientProperties;
import com.taoking.spring3.order.config.OrderProperties;
import com.taoking.spring3.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orderService;
    private final OrderProperties orderProperties;
    private final CatalogClientProperties catalogClientProperties;

    OrderController(
            OrderService orderService,
            OrderProperties orderProperties,
            CatalogClientProperties catalogClientProperties
    ) {
        this.orderService = orderService;
        this.orderProperties = orderProperties;
        this.catalogClientProperties = catalogClientProperties;
    }

    @PostMapping("/preview")
    OrderPreviewResponse preview(
            @Valid @RequestBody OrderPreviewRequest request,
            @RequestParam(defaultValue = "false") boolean slowCatalog,
            @RequestParam(defaultValue = "false") boolean failCatalog
    ) {
        return orderService.preview(request, slowCatalog, failCatalog);
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
