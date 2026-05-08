package com.taoking.spring3.common.dto;

public record FieldErrorResponse(
        String field,
        String message
) {
}
