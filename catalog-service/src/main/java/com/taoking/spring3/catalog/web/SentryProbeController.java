package com.taoking.spring3.catalog.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/admin")
class SentryProbeController {

    @PostMapping("/sentry-error")
    @PreAuthorize("hasRole('ADMIN')")
    void triggerSentryError() {
        throw new IllegalStateException("Sentry probe from catalog-service");
    }
}
