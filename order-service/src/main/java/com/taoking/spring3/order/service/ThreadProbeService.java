package com.taoking.spring3.order.service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ThreadProbeService {

    private static final Logger log = LoggerFactory.getLogger(ThreadProbeService.class);

    public ThreadProbeResponse waitOnRequestThread(long delayMs) {
        return capture("request", delayMs);
    }

    @Async("demoTaskExecutor")
    public CompletableFuture<ThreadProbeResponse> waitOnAsyncExecutor(long delayMs) {
        return CompletableFuture.completedFuture(capture("async", delayMs));
    }

    private ThreadProbeResponse capture(String mode, long delayMs) {
        Thread thread = Thread.currentThread();
        sleep(delayMs);
        ThreadProbeResponse response = new ThreadProbeResponse(
                mode,
                thread.getName(),
                thread.isVirtual(),
                delayMs
        );
        log.info("Thread probe mode={} thread={} virtual={} delayMs={}",
                response.mode(),
                response.threadName(),
                response.virtual(),
                response.delayMs());
        return response;
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(Duration.ofMillis(delayMs));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread probe was interrupted", ex);
        }
    }
}
