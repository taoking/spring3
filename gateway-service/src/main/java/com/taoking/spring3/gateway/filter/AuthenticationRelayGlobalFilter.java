package com.taoking.spring3.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
class AuthenticationRelayGlobalFilter implements GlobalFilter, Ordered {

    static final String AUTH_TYPE_HEADER = "X-Gateway-Auth-Type";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(AUTH_TYPE_HEADER, resolveAuthType(authorization)))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private String resolveAuthType(String authorization) {
        if (authorization.regionMatches(true, 0, "Basic ", 0, "Basic ".length())) {
            return "Basic";
        }
        if (authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return "Bearer";
        }
        return "Other";
    }
}
