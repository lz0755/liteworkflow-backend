package com.liteworkflow.ai.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("liteworkflow.ai")
public class AiProperties {

    @NotBlank private String provider;
    @NotBlank private String baseUrl;
    @NotBlank private String apiKey;
    @NotBlank private String chatModel;
    @NotBlank private String coreServiceUrl;
    @NotBlank private String internalToken;
    @NotNull private Duration connectTimeout = Duration.ofSeconds(10);
    @NotNull private Duration requestTimeout = Duration.ofSeconds(60);
    @Min(1) private int maxOutputTokens = 2000;
    @Min(1) private int maxConcurrentRequests = 8;
    @NotNull private Duration concurrencyAcquireTimeout = Duration.ofMillis(100);
    @Min(1) private int dailyRequestLimit = 100;
    @Min(1) private long dailyTokenLimit = 200_000;

    @AssertTrue(message = "liteworkflow.ai.provider must be openai")
    public boolean isSupportedProvider() {
        return "openai".equalsIgnoreCase(provider);
    }

    @AssertTrue(message = "liteworkflow.ai.base-url must be an absolute HTTP(S) URL")
    public boolean isValidBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(baseUrl);
            return isResolvedValue(baseUrl)
                    && uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @AssertTrue(message = "liteworkflow.ai.api-key must be configured with a real external API key")
    public boolean isValidApiKey() {
        return apiKey == null || apiKey.isBlank() || isResolvedValue(apiKey);
    }

    @AssertTrue(message = "liteworkflow.ai.chat-model must be configured with a real model name")
    public boolean isValidChatModel() {
        return chatModel == null || chatModel.isBlank() || isResolvedValue(chatModel);
    }

    @AssertTrue(message = "liteworkflow.ai.core-service-url must be an absolute HTTP(S) URL")
    public boolean isValidCoreServiceUrl() {
        return isValidHttpUrl(coreServiceUrl);
    }

    @AssertTrue(message = "liteworkflow.ai.internal-token must be a configured service credential")
    public boolean isValidInternalToken() {
        return internalToken == null || internalToken.isBlank() || isResolvedValue(internalToken);
    }

    @AssertTrue(message = "AI timeouts must be positive")
    public boolean areTimeoutsPositive() {
        return positive(connectTimeout) && positive(requestTimeout) && positive(concurrencyAcquireTimeout);
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    private static boolean isResolvedValue(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase();
        return !normalized.contains("${")
                && !normalized.startsWith("replace_me")
                && !normalized.startsWith("replace-me");
    }

    private static boolean isValidHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(value);
            return isResolvedValue(value)
                    && uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getCoreServiceUrl() { return coreServiceUrl; }
    public void setCoreServiceUrl(String coreServiceUrl) { this.coreServiceUrl = coreServiceUrl; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(int maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
    public Duration getConcurrencyAcquireTimeout() { return concurrencyAcquireTimeout; }
    public void setConcurrencyAcquireTimeout(Duration concurrencyAcquireTimeout) { this.concurrencyAcquireTimeout = concurrencyAcquireTimeout; }
    public int getDailyRequestLimit() { return dailyRequestLimit; }
    public void setDailyRequestLimit(int dailyRequestLimit) { this.dailyRequestLimit = dailyRequestLimit; }
    public long getDailyTokenLimit() { return dailyTokenLimit; }
    public void setDailyTokenLimit(long dailyTokenLimit) { this.dailyTokenLimit = dailyTokenLimit; }
}
