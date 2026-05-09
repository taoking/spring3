package com.taoking.spring3.order.web;

import com.taoking.spring3.common.dto.OrderPreviewRequest;
import com.taoking.spring3.common.dto.OrderPreviewResponse;
import com.taoking.spring3.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
class OrderVersionedController {

    private final OrderService orderService;

    OrderVersionedController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/v1/orders/preview")
    OrderPreviewResponse previewV1(
            @Valid @RequestBody OrderPreviewRequest request,
            @RequestParam(defaultValue = "false") boolean slowCatalog,
            @RequestParam(defaultValue = "false") boolean failCatalog,
            @RequestParam(defaultValue = "false") boolean rateLimit,
            @RequestParam(defaultValue = "false") boolean bulkhead,
            @RequestParam(defaultValue = "false") boolean holdBulkhead,
            @RequestParam(defaultValue = "false") boolean sentinelFlow,
            @RequestParam(defaultValue = "false") boolean sentinelHotSku
    ) {
        return preview(
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

    @PostMapping("/v2/orders/preview")
    OrderPreviewV2Response previewV2(
            @Valid @RequestBody OrderPreviewRequest request,
            @RequestParam(defaultValue = "false") boolean slowCatalog,
            @RequestParam(defaultValue = "false") boolean failCatalog,
            @RequestParam(defaultValue = "false") boolean rateLimit,
            @RequestParam(defaultValue = "false") boolean bulkhead,
            @RequestParam(defaultValue = "false") boolean holdBulkhead,
            @RequestParam(defaultValue = "false") boolean sentinelFlow,
            @RequestParam(defaultValue = "false") boolean sentinelHotSku
    ) {
        OrderPreviewResponse preview = preview(
                request,
                slowCatalog,
                failCatalog,
                rateLimit,
                bulkhead,
                holdBulkhead,
                sentinelFlow,
                sentinelHotSku
        );
        return new OrderPreviewV2Response(
                "v2",
                preview,
                Map.of(
                        "self", "/api/v2/orders/preview",
                        "previous", "/api/v1/orders/preview"
                )
        );
    }

    private OrderPreviewResponse preview(
            OrderPreviewRequest request,
            boolean slowCatalog,
            boolean failCatalog,
            boolean rateLimit,
            boolean bulkhead,
            boolean holdBulkhead,
            boolean sentinelFlow,
            boolean sentinelHotSku
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
}
