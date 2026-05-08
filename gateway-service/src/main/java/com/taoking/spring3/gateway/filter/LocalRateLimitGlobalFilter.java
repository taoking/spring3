package com.taoking.spring3.gateway.filter;

import com.taoking.spring3.gateway.config.LocalRateLimitProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
class LocalRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final LocalRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    LocalRateLimitGlobalFilter(LocalRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LocalRateLimitGlobalFilter(LocalRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled() || shouldSkip(exchange)) {
            return chain.filter(exchange);
        }

        WindowCounter counter = counters.computeIfAbsent(resolveKey(exchange), ignored -> new WindowCounter(now()));
        int count = counter.incrementAndGet(now(), properties.window().toMillis());
        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(properties.requestsPerWindow()));
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(Math.max(0, properties.requestsPerWindow() - count)));

        if (count > properties.requestsPerWindow()) {
            return reject(exchange);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private boolean shouldSkip(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return path.startsWith("/actuator/") || path.startsWith("/fallback/");
    }

    private String resolveKey(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization)) {
            return "auth:" + fingerprint(authorization);
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return "ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "anonymous";
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private long now() {
        return clock.millis();
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(Math.max(1, properties.window().toSeconds())));
        byte[] body = """
                {"title":"Gateway rate limit exceeded","status":429}
                """.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private static final class WindowCounter {
        private volatile long windowStartMs;
        private final AtomicInteger count = new AtomicInteger();

        private WindowCounter(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }

        private synchronized int incrementAndGet(long now, long windowMs) {
            if (now - windowStartMs >= windowMs) {
                windowStartMs = now;
                count.set(0);
            }
            return count.incrementAndGet();
        }
    }
}
