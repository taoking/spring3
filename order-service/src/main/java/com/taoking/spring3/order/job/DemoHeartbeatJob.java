package com.taoking.spring3.order.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DemoHeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(DemoHeartbeatJob.class);

    private final Counter heartbeatCounter;

    DemoHeartbeatJob(MeterRegistry meterRegistry) {
        this.heartbeatCounter = Counter.builder("demo.scheduler.heartbeat.total")
                .description("Number of scheduler heartbeat executions")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${demo.order.heartbeat-delay}")
    void heartbeat() {
        heartbeatCounter.increment();
        log.info("Scheduler heartbeat at {}", Instant.now());
    }
}
