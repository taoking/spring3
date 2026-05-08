package com.taoking.spring3.gateway.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
class GatewayFallbackController {

    @GetMapping("/{service}")
    Mono<ResponseEntity<Map<String, Object>>> getFallback(@PathVariable String service) {
        return fallback(service);
    }

    @PostMapping("/{service}")
    Mono<ResponseEntity<Map<String, Object>>> postFallback(@PathVariable String service) {
        return fallback(service);
    }

    private Mono<ResponseEntity<Map<String, Object>>> fallback(String service) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "title", "Gateway fallback",
                        "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "service", service,
                        "timestamp", Instant.now().toString()
                )));
    }
}
