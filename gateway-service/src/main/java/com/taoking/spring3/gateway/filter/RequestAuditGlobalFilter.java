package com.taoking.spring3.gateway.filter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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

    private final Tracer tracer;

    RequestAuditGlobalFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracer = tracerProvider.getIfAvailable();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant startedAt = Instant.now();
        String requestId = resolveRequestId(exchange);
        TraceIds traceIds = resolveTraceIds(exchange);
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
                    String routeId = route == null ? "unmatched" : route.getId();
                    String statusValue = status == null ? "NA" : String.valueOf(status.value());
                    log.atInfo()
                            .addKeyValue("event", "gateway.request")
                            .addKeyValue("requestId", requestId)
                            .addKeyValue("traceAvailable", traceIds.isAvailable())
                            .addKeyValue("routeId", routeId)
                            .addKeyValue("status", statusValue)
                            .addKeyValue("elapsedMs", elapsedMs)
                            .log("gateway requestId={} traceId={} spanId={} routeId={} status={} elapsedMs={}",
                                    requestId,
                                    traceIds.traceId(),
                                    traceIds.spanId(),
                                    routeId,
                                    statusValue,
                                    elapsedMs);
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

    private TraceIds resolveTraceIds(ServerWebExchange exchange) {
        Span currentSpan = tracer == null ? null : tracer.currentSpan();
        if (currentSpan != null) {
            return new TraceIds(currentSpan.context().traceId(), currentSpan.context().spanId());
        }
        String traceparent = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (!StringUtils.hasText(traceparent)) {
            return TraceIds.empty();
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4 || parts[1].length() != 32 || parts[2].length() != 16) {
            return TraceIds.empty();
        }
        return new TraceIds(parts[1], parts[2]);
    }

    private record TraceIds(String traceId, String spanId) {
        boolean isAvailable() {
            return !"-".equals(traceId) && !"-".equals(spanId);
        }

        static TraceIds empty() {
            return new TraceIds("-", "-");
        }
    }
}
