package com.taoking.spring3.catalog.domain;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String sku) {
        super("Product not found: " + sku);
    }
}
