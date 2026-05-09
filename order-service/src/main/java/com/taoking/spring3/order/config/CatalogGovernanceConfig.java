package com.taoking.spring3.order.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CatalogGovernanceConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService catalogGovernanceExecutor(CatalogGovernanceProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task);
            thread.setName("catalog-governance-" + sequence.incrementAndGet());
            return thread;
        };
        return Executors.newFixedThreadPool(properties.asyncPoolSize(), threadFactory);
    }
}
