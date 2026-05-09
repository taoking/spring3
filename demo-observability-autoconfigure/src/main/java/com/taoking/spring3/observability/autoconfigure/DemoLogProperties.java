package com.taoking.spring3.observability.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.observability.demolog")
public class DemoLogProperties {

    private boolean enabled = true;

    private Duration slowThreshold = Duration.ofMillis(500);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getSlowThreshold() {
        return slowThreshold;
    }

    public void setSlowThreshold(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
    }
}
