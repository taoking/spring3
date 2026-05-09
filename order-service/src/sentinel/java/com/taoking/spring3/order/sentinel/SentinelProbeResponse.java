package com.taoking.spring3.order.sentinel;

record SentinelProbeResponse(
        String resource,
        String outcome,
        boolean slowCall
) {
}
