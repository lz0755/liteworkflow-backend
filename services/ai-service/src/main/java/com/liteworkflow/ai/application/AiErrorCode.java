package com.liteworkflow.ai.application;

import com.liteworkflow.common.core.error.ErrorCode;

public enum AiErrorCode implements ErrorCode {
    CONVERSATION_NOT_FOUND("AI_CONVERSATION_404", "AI conversation was not found", 404),
    CONVERSATION_FORBIDDEN("AI_CONVERSATION_403", "AI conversation access is forbidden", 403),
    INVALID_REQUEST("AI_REQUEST_400", "AI request is invalid", 400),
    RESOURCE_NOT_FOUND("AI_RESOURCE_404", "The requested resource was not found", 404),
    RESOURCE_FORBIDDEN("AI_RESOURCE_403", "AI access to the requested resource is forbidden", 403),
    CORE_UNAVAILABLE("AI_CORE_502", "Core resource context is unavailable", 502),
    CONCURRENCY_LIMIT("AI_CONCURRENCY_429", "Too many concurrent AI requests", 429),
    STREAM_CONCURRENCY_LIMIT("AI_STREAM_CONCURRENCY_429", "Too many concurrent AI streams", 429),
    DAILY_REQUEST_LIMIT("AI_DAILY_REQUEST_429", "Daily AI request limit exceeded", 429),
    DAILY_TOKEN_LIMIT("AI_DAILY_TOKEN_429", "Daily AI token limit exceeded", 429),
    PROVIDER_RATE_LIMITED("AI_PROVIDER_429", "AI provider rate limit was exceeded", 502),
    PROVIDER_UNAVAILABLE("AI_PROVIDER_502", "AI provider is unavailable", 502),
    PROVIDER_TIMEOUT("AI_PROVIDER_504", "AI provider request timed out", 504),
    INVALID_STRUCTURED_OUTPUT("AI_OUTPUT_502", "AI provider returned an invalid structured response", 502),
    EMPTY_STREAM_OUTPUT("AI_STREAM_OUTPUT_502", "AI provider returned an empty stream", 502),
    STREAM_FINALIZATION_FAILED("AI_STREAM_500", "AI stream could not be finalized", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    AiErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public int httpStatus() { return httpStatus; }
}
