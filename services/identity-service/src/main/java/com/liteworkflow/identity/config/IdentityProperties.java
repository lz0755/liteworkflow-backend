package com.liteworkflow.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("liteworkflow.identity")
public class IdentityProperties {

    private Duration refreshTokenTtl = Duration.ofDays(30);
    private Duration resetTokenTtl = Duration.ofMinutes(30);
    private String passwordResetUrl = "http://localhost:3000/reset-password";
    private LoginLimit loginLimit = new LoginLimit();
    private Outbox outbox = new Outbox();

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = requirePositive(refreshTokenTtl, "refreshTokenTtl");
    }

    public Duration getResetTokenTtl() {
        return resetTokenTtl;
    }

    public void setResetTokenTtl(Duration resetTokenTtl) {
        this.resetTokenTtl = requirePositive(resetTokenTtl, "resetTokenTtl");
    }

    public String getPasswordResetUrl() {
        return passwordResetUrl;
    }

    public void setPasswordResetUrl(String passwordResetUrl) {
        if (passwordResetUrl == null || passwordResetUrl.isBlank()) {
            throw new IllegalArgumentException("passwordResetUrl must not be blank");
        }
        this.passwordResetUrl = passwordResetUrl;
    }

    public LoginLimit getLoginLimit() {
        return loginLimit;
    }

    public void setLoginLimit(LoginLimit loginLimit) {
        this.loginLimit = loginLimit == null ? new LoginLimit() : loginLimit;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox == null ? new Outbox() : outbox;
    }

    public static class LoginLimit {
        private int maxFailures = 5;
        private Duration window = Duration.ofMinutes(15);

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            if (maxFailures < 1) {
                throw new IllegalArgumentException("loginLimit.maxFailures must be at least one");
            }
            this.maxFailures = maxFailures;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = requirePositive(window, "loginLimit.window");
        }
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
