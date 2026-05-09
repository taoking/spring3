package com.taoking.spring3.order.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
class OrderPreviewEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPreviewEventListener.class);

    private final Counter eventCounter;

    OrderPreviewEventListener(MeterRegistry meterRegistry) {
        this.eventCounter = Counter.builder("orders.preview.events.total")
                .description("Number of handled order preview events")
                .register(meterRegistry);
    }

    @Async("demoTaskExecutor")
    @EventListener
    void onOrderPreviewCreated(OrderPreviewCreatedEvent event) {
        eventCounter.increment();
        Thread thread = Thread.currentThread();
        log.info("Handled order preview event orderId={} createdAt={} thread={} virtual={}",
                event.preview().orderId(),
                event.createdAt(),
                thread.getName(),
                thread.isVirtual());
    }
}
