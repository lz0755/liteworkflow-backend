package com.liteworkflow.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("liteworkflow.core")
public class CoreProperties {

    private boolean permissionCacheEnabled = true;
    private String internalToken = "change-me-internal-token";
    private Outbox outbox = new Outbox();

    public boolean isPermissionCacheEnabled() {
        return permissionCacheEnabled;
    }

    public void setPermissionCacheEnabled(boolean permissionCacheEnabled) {
        this.permissionCacheEnabled = permissionCacheEnabled;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException("internalToken must not be blank");
        }
        this.internalToken = internalToken;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox == null ? new Outbox() : outbox;
    }

    public static class Outbox {
        private int maxRetries = 12;
        private Duration retryDelay = Duration.ofSeconds(10);
        private Duration publisherConfirmTimeout = Duration.ofSeconds(5);
        private boolean immediatePublish = true;
        private boolean schedulingEnabled = true;

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            if (maxRetries < 1) {
                throw new IllegalArgumentException("outbox.maxRetries must be at least one");
            }
            this.maxRetries = maxRetries;
        }

        public Duration getRetryDelay() {
            return retryDelay;
        }

        public void setRetryDelay(Duration retryDelay) {
            this.retryDelay = requirePositive(retryDelay, "outbox.retryDelay");
        }

        public Duration getPublisherConfirmTimeout() {
            return publisherConfirmTimeout;
        }

        public void setPublisherConfirmTimeout(Duration publisherConfirmTimeout) {
            this.publisherConfirmTimeout = requirePositive(
                    publisherConfirmTimeout, "outbox.publisherConfirmTimeout");
        }

        public boolean isImmediatePublish() {
            return immediatePublish;
        }

        public void setImmediatePublish(boolean immediatePublish) {
            this.immediatePublish = immediatePublish;
        }

        public boolean isSchedulingEnabled() {
            return schedulingEnabled;
        }

        public void setSchedulingEnabled(boolean schedulingEnabled) {
            this.schedulingEnabled = schedulingEnabled;
        }
    }

    private static Duration requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
