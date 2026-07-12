package com.liteworkflow.infra.export;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("liteworkflow.export")
public class InfraExportProperties {

    private boolean schedulingEnabled = true;
    private int maxPublishAttempts = 12;
    private Duration retryDelay = Duration.ofSeconds(10);
    private Duration publisherConfirmTimeout = Duration.ofSeconds(5);

    public boolean isSchedulingEnabled() { return schedulingEnabled; }
    public void setSchedulingEnabled(boolean value) { schedulingEnabled = value; }
    public int getMaxPublishAttempts() { return maxPublishAttempts; }
    public void setMaxPublishAttempts(int value) {
        if (value < 1) throw new IllegalArgumentException("maxPublishAttempts must be positive");
        maxPublishAttempts = value;
    }
    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration value) { retryDelay = positive(value, "retryDelay"); }
    public Duration getPublisherConfirmTimeout() { return publisherConfirmTimeout; }
    public void setPublisherConfirmTimeout(Duration value) {
        publisherConfirmTimeout = positive(value, "publisherConfirmTimeout");
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
