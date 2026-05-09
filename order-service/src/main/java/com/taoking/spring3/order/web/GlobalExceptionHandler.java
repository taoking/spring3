package com.taoking.spring3.order.web;

import com.taoking.spring3.common.dto.FieldErrorResponse;
import com.taoking.spring3.order.service.SentinelBlockedException;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ProblemDetail handleValidation(Exception ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        detail.setTitle("Validation failed");
        detail.setType(URI.create("https://spring3.local/problems/validation"));
        detail.setProperty("path", request.getRequestURI());
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
    ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access is denied");
        detail.setTitle("Forbidden");
        detail.setType(URI.create("https://spring3.local/problems/forbidden"));
        detail.setProperty("path", request.getRequestURI());
        return detail;
    }

    @ExceptionHandler(SentinelBlockedException.class)
    ProblemDetail handleSentinelBlocked(SentinelBlockedException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Sentinel request blocked");
        detail.setTitle("Sentinel blocked");
        detail.setType(URI.create("https://spring3.local/problems/sentinel-blocked"));
        detail.setProperty("path", request.getRequestURI());
        detail.setProperty("resource", ex.resource());
        detail.setProperty("strategy", ex.strategy());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), ex);
        Sentry.captureException(ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
        detail.setTitle("Internal server error");
        detail.setType(URI.create("https://spring3.local/problems/internal-server-error"));
        detail.setProperty("path", request.getRequestURI());
        return detail;
    }
}
