package com.taoking.spring3.order.service;

public record ThreadProbeResponse(
        String mode,
        String threadName,
        boolean virtual,
        long delayMs
) {
}
