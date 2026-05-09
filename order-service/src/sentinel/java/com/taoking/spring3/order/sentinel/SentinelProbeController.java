package com.taoking.spring3.order.sentinel;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("sentinel")
class SentinelProbeController {

    private final SentinelOrderTrafficGuard trafficGuard;

    SentinelProbeController(SentinelOrderTrafficGuard trafficGuard) {
        this.trafficGuard = trafficGuard;
    }

    @GetMapping("/api/orders/sentinel/degrade-probe")
    SentinelProbeResponse degradeProbe(@RequestParam(defaultValue = "false") boolean slow) {
        return trafficGuard.checkDegradeProbe(slow);
    }
}
