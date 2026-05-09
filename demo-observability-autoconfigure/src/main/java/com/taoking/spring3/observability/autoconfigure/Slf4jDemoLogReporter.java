package com.taoking.spring3.observability.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jDemoLogReporter implements DemoLogReporter {

    private static final Logger log = LoggerFactory.getLogger(Slf4jDemoLogReporter.class);

    @Override
    public void report(DemoLogRecord record) {
        if (record.slow()) {
            log.warn("demoLog operation={} method={} elapsedMs={} slow=true",
                    record.operation(),
                    record.method(),
                    record.elapsedMs());
            return;
        }
        log.info("demoLog operation={} method={} elapsedMs={}",
                record.operation(),
                record.method(),
                record.elapsedMs());
    }
}
