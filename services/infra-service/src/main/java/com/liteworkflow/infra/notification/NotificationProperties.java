package com.liteworkflow.infra.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("liteworkflow.notification")
public class NotificationProperties {

    private String coreServiceUrl = "http://localhost:8082";
    private String internalToken = "change-me-internal-token";
    private String webBaseUrl = "http://localhost:3000";
    private Email email = new Email();

    public String getCoreServiceUrl() {
        return coreServiceUrl;
    }

    public void setCoreServiceUrl(String coreServiceUrl) {
        this.coreServiceUrl = requireText(coreServiceUrl, "coreServiceUrl");
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = requireText(internalToken, "internalToken");
    }

    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    public void setWebBaseUrl(String webBaseUrl) {
        this.webBaseUrl = requireText(webBaseUrl, "webBaseUrl").replaceAll("/+$", "");
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email == null ? new Email() : email;
    }

    public static class Email {

        private String from = "no-reply@liteworkflow.local";
        private int maxAttempts = 5;
        private Duration retryDelay = Duration.ofSeconds(10);
        private int batchSize = 25;
        private boolean schedulingEnabled = true;

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = requireText(from, "email.from");
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            if (maxAttempts < 1 || maxAttempts > 20) {
                throw new IllegalArgumentException("email.maxAttempts must be between 1 and 20");
            }
            this.maxAttempts = maxAttempts;
        }

        public Duration getRetryDelay() {
            return retryDelay;
        }

        public void setRetryDelay(Duration retryDelay) {
            if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
                throw new IllegalArgumentException("email.retryDelay must be positive");
            }
            this.retryDelay = retryDelay;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            if (batchSize < 1 || batchSize > 100) {
                throw new IllegalArgumentException("email.batchSize must be between 1 and 100");
            }
            this.batchSize = batchSize;
        }

        public boolean isSchedulingEnabled() {
            return schedulingEnabled;
        }

        public void setSchedulingEnabled(boolean schedulingEnabled) {
            this.schedulingEnabled = schedulingEnabled;
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
