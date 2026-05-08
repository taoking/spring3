package com.taoking.spring3.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderPreviewRequest(
        @NotBlank(message = "sku must not be blank")
        String sku,

        @Positive(message = "quantity must be greater than zero")
        int quantity
) {
}
