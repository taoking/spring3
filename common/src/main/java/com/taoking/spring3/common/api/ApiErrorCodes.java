package com.taoking.spring3.common.api;

public final class ApiErrorCodes {

    public static final String CATALOG_PRODUCT_NOT_FOUND = "CATALOG_PRODUCT_NOT_FOUND";
    public static final String CATALOG_SIMULATED_FAILURE = "CATALOG_SIMULATED_FAILURE";
    public static final String CATALOG_VALIDATION_FAILED = "CATALOG_VALIDATION_FAILED";
    public static final String ORDER_SENTINEL_BLOCKED = "ORDER_SENTINEL_BLOCKED";
    public static final String ORDER_VALIDATION_FAILED = "ORDER_VALIDATION_FAILED";
    public static final String SECURITY_ACCESS_DENIED = "SECURITY_ACCESS_DENIED";
    public static final String SYSTEM_INTERNAL_ERROR = "SYSTEM_INTERNAL_ERROR";

    private ApiErrorCodes() {
    }
}
