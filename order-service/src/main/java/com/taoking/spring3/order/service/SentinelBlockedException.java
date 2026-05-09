package com.taoking.spring3.order.service;

public class SentinelBlockedException extends RuntimeException {

    private final String resource;
    private final String strategy;

    public SentinelBlockedException(String resource, String strategy, Throwable cause) {
        super("Sentinel blocked resource %s by %s".formatted(resource, strategy), cause);
        this.resource = resource;
        this.strategy = strategy;
    }

    public String resource() {
        return resource;
    }

    public String strategy() {
        return strategy;
    }
}
