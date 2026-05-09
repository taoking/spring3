package com.taoking.spring3.order.service;

public class CatalogLookupFailedException extends RuntimeException {

    CatalogLookupFailedException(String sku) {
        super("Catalog lookup failed after downstream fallback for sku: " + sku);
    }
}
