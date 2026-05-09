package com.taoking.spring3.observability.autoconfigure;

import com.taoking.spring3.common.aop.DemoLog;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.util.StopWatch;

@Aspect
public class DemoLogAspect {

    private final DemoLogProperties properties;
    private final DemoLogReporter reporter;

    public DemoLogAspect(DemoLogProperties properties, DemoLogReporter reporter) {
        this.properties = properties;
        this.reporter = reporter;
    }

    @Around("@annotation(demoLog)")
    public Object logExecution(ProceedingJoinPoint joinPoint, DemoLog demoLog) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();
            long elapsedMs = stopWatch.getTotalTimeMillis();
            reporter.report(new DemoLogRecord(
                    demoLog.value(),
                    joinPoint.getSignature().toShortString(),
                    elapsedMs,
                    isSlow(elapsedMs)
            ));
        }
    }

    private boolean isSlow(long elapsedMs) {
        Duration threshold = properties.getSlowThreshold();
        return threshold != null && elapsedMs >= threshold.toMillis();
    }
}
