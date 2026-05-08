package com.taoking.spring3.catalog.domain;

public class SimulatedCatalogException extends RuntimeException {
    public SimulatedCatalogException(String sku) {
        super("Simulated catalog failure for sku: " + sku);
    }
}
