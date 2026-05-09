package com.taoking.spring3.order.web;

import com.taoking.spring3.common.api.ApiErrorCodes;
import com.taoking.spring3.common.api.ApiHeaders;
import com.taoking.spring3.common.dto.FieldErrorResponse;
import com.taoking.spring3.order.service.SentinelBlockedException;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ProblemDetail handleValidation(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        ProblemDetail detail = problemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "Request validation failed",
                "https://spring3.local/problems/validation",
                ApiErrorCodes.ORDER_VALIDATION_FAILED,
                request,
                response
        );
        if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            List<FieldErrorResponse> errors = methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                    .toList();
            detail.setProperty("fieldErrors", errors);
        }
        return detail;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail handleAuthorizationDenied(
            AuthorizationDeniedException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return problemDetail(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "Access is denied",
                "https://spring3.local/problems/forbidden",
                ApiErrorCodes.SECURITY_ACCESS_DENIED,
                request,
                response
        );
    }

    @ExceptionHandler(SentinelBlockedException.class)
    ProblemDetail handleSentinelBlocked(
            SentinelBlockedException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        ProblemDetail detail = problemDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Sentinel blocked",
                "Sentinel request blocked",
                "https://spring3.local/problems/sentinel-blocked",
                ApiErrorCodes.ORDER_SENTINEL_BLOCKED,
                request,
                response
        );
        detail.setProperty("resource", ex.resource());
        detail.setProperty("strategy", ex.strategy());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        log.error("Unhandled exception path={}", request.getRequestURI(), ex);
        Sentry.captureException(ex);
        return problemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "Unexpected server error",
                "https://spring3.local/problems/internal-server-error",
                ApiErrorCodes.SYSTEM_INTERNAL_ERROR,
                request,
                response
        );
    }

    private ProblemDetail problemDetail(
            HttpStatus status,
            String title,
            String message,
            String type,
            String errorCode,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setType(URI.create(type));
        detail.setProperty("path", request.getRequestURI());
        detail.setProperty("errorCode", errorCode);
        detail.setProperty("requestId", requestId(request, response));
        detail.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return detail;
    }

    private String requestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = request.getHeader(ApiHeaders.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = MDC.get("requestId");
        }
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(ApiHeaders.REQUEST_ID, requestId);
        return requestId;
    }
}
