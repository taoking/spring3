package com.taoking.spring3.catalog.aop;

import com.taoking.spring3.common.aop.DemoLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect
@Component
class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("@annotation(demoLog)")
    Object logExecution(ProceedingJoinPoint joinPoint, DemoLog demoLog) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();
            log.info("demoLog operation={} method={} elapsedMs={}",
                    demoLog.value(),
                    joinPoint.getSignature().toShortString(),
                    stopWatch.getTotalTimeMillis());
        }
    }
}
