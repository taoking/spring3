package com.taoking.spring3.observability.autoconfigure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class DemoHttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DemoHttpRequestLoggingFilter.class);

    private final DemoHttpRequestLoggingProperties properties;

    public DemoHttpRequestLoggingFilter(DemoHttpRequestLoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Instant startedAt = Instant.now();
        String requestId = resolveRequestId(request);
        String previousRequestId = MDC.get("requestId");
        MDC.put("requestId", requestId);
        response.setHeader(properties.getRequestIdHeader(), requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
            TraceIds traceIds = resolveTraceIds(request);
            log.atInfo()
                    .addKeyValue("event", "http.request")
                    .addKeyValue("traceAvailable", traceIds.isAvailable())
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("elapsedMs", elapsedMs)
                    .addKeyValue("authScheme", resolveAuthorizationScheme(request))
                    .log("http request completed");
            restoreMdcRequestId(previousRequestId);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(properties.getRequestIdHeader());
        return StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();
    }

    private String resolveAuthorizationScheme(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            return "-";
        }
        int separator = authorization.indexOf(' ');
        return separator > 0 ? authorization.substring(0, separator) : "present";
    }

    private TraceIds resolveTraceIds(HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        String spanId = MDC.get("spanId");
        if (StringUtils.hasText(traceId) && StringUtils.hasText(spanId)) {
            return new TraceIds(traceId, spanId);
        }
        String traceparent = request.getHeader("traceparent");
        if (!StringUtils.hasText(traceparent)) {
            return TraceIds.empty();
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4 || parts[1].length() != 32 || parts[2].length() != 16) {
            return TraceIds.empty();
        }
        return new TraceIds(parts[1], parts[2]);
    }

    private void restoreMdcRequestId(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove("requestId");
            return;
        }
        MDC.put("requestId", previousRequestId);
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
