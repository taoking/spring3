package com.taoking.spring3.observability.autoconfigure;

import com.taoking.spring3.common.aop.DemoLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({DemoLog.class, Aspect.class, ProceedingJoinPoint.class})
@ConditionalOnProperty(prefix = "demo.observability.demolog", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DemoLogProperties.class)
public class DemoLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DemoLogReporter demoLogReporter() {
        return new Slf4jDemoLogReporter();
    }

    @Bean
    @ConditionalOnMissingBean
    DemoLogAspect demoLogAspect(DemoLogProperties properties, DemoLogReporter reporter) {
        return new DemoLogAspect(properties, reporter);
    }
}
