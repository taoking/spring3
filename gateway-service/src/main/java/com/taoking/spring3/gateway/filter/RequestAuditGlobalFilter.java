package com.taoking.spring3.gateway.filter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
class RequestAuditGlobalFilter implements GlobalFilter, Ordered {

    static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestAuditGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant startedAt = Instant.now();
        String requestId = resolveRequestId(exchange);
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, requestId))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
        mutatedExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return chain.filter(mutatedExchange)
                .doFinally(signal -> {
                    long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
                    Route route = mutatedExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    HttpStatusCode status = mutatedExchange.getResponse().getStatusCode();
                    log.info(
                            "gateway requestId={} routeId={} status={} elapsedMs={}",
                            requestId,
                            route == null ? "unmatched" : route.getId(),
                            status == null ? "NA" : status.value(),
                            elapsedMs
                    );
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        String incoming = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        return StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();
    }
}
