package com.taoking.spring3.order.service;

import com.taoking.spring3.order.config.OrderProperties;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final OrderProperties properties;

    public NotificationService(OrderProperties properties) {
        this.properties = properties;
    }

    @Async("demoTaskExecutor")
    public CompletableFuture<Void> sendPreviewNotification(String orderId) {
        try {
            Thread.sleep(properties.notificationDelay().toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(ex);
        }
        log.info("Async notification finished for orderId={}", orderId);
        return CompletableFuture.completedFuture(null);
    }
}
