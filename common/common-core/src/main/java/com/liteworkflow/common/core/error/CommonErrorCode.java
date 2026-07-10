package com.liteworkflow.common.core.error;

public enum CommonErrorCode implements ErrorCode {
    VALIDATION_ERROR("COMMON_400", "Request validation failed", 400),
    UNAUTHORIZED("COMMON_401", "Authentication is required", 401),
    FORBIDDEN("COMMON_403", "Access is forbidden", 403),
    NOT_FOUND("COMMON_404", "Resource was not found", 404),
    CONFLICT("COMMON_409", "Resource state conflicts with the request", 409),
    TOO_MANY_REQUESTS("COMMON_429", "Too many requests", 429),
    INTERNAL_ERROR("COMMON_500", "An internal error occurred", 500),
    SERVICE_UNAVAILABLE("COMMON_503", "Service is temporarily unavailable", 503);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    CommonErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
