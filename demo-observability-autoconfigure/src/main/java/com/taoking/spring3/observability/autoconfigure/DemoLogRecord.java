package com.taoking.spring3.observability.autoconfigure;

public record DemoLogRecord(
        String operation,
        String method,
        long elapsedMs,
        boolean slow
) {
}
