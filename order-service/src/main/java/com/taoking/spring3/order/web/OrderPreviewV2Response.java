package com.taoking.spring3.order.web;

import com.taoking.spring3.common.dto.OrderPreviewResponse;
import java.util.Map;

public record OrderPreviewV2Response(
        String apiVersion,
        OrderPreviewResponse data,
        Map<String, String> links
) {
}
