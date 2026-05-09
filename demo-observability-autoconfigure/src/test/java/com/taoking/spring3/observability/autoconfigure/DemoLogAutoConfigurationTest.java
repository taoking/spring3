package com.taoking.spring3.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.taoking.spring3.common.aop.DemoLog;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoLogAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, DemoLogAutoConfiguration.class));

    @Test
    void autoConfiguresDemoLogAspectAndDefaultReporter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DemoLogProperties.class);
            assertThat(context).hasSingleBean(DemoLogReporter.class);
            assertThat(context).hasSingleBean(DemoLogAspect.class);
            assertThat(context.getBean(DemoLogProperties.class).isEnabled()).isTrue();
        });
    }

    @Test
    void disabledPropertyBacksOffAutoConfiguration() {
        contextRunner
                .withPropertyValues("demo.observability.demolog.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoLogProperties.class);
                    assertThat(context).doesNotHaveBean(DemoLogReporter.class);
                    assertThat(context).doesNotHaveBean(DemoLogAspect.class);
                });
    }

    @Test
    void userReporterOverridesDefaultReporterAndReceivesAspectEvents() {
        contextRunner
                .withBean(DemoLogReporter.class, CapturingDemoLogReporter::new)
                .withBean(SampleService.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DemoLogReporter.class);
                    assertThat(context).hasSingleBean(DemoLogAspect.class);

                    context.getBean(SampleService.class).work();

                    CapturingDemoLogReporter reporter = (CapturingDemoLogReporter) context.getBean(DemoLogReporter.class);
                    assertThat(reporter.records())
                            .singleElement()
                            .satisfies(record -> {
                                assertThat(record.operation()).isEqualTo("sample.work");
                                assertThat(record.method()).contains("SampleService.work");
                            });
                });
    }

    @Test
    void userAspectOverridesDefaultAspect() {
        DemoLogProperties properties = new DemoLogProperties();
        CapturingDemoLogReporter reporter = new CapturingDemoLogReporter();
        contextRunner
                .withBean(DemoLogAspect.class, () -> new DemoLogAspect(properties, reporter))
                .run(context -> assertThat(context).hasSingleBean(DemoLogAspect.class));
    }

    static class SampleService {

        @DemoLog("sample.work")
        public String work() {
            return "ok";
        }
    }

    static class CapturingDemoLogReporter implements DemoLogReporter {

        private final List<DemoLogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void report(DemoLogRecord record) {
            records.add(record);
        }

        List<DemoLogRecord> records() {
            return records;
        }
    }
}
