package com.liteworkflow.common.core.api;

import com.liteworkflow.common.core.error.ErrorCode;
import com.liteworkflow.common.core.trace.TraceIds;
import java.time.Instant;
import java.util.Objects;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp) {

    public static final String SUCCESS_CODE = "OK";

    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "Success", data, TraceIds.current(), Instant.now());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.defaultMessage());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        String resolvedMessage = message == null || message.isBlank() ? errorCode.defaultMessage() : message;
        return new ApiResponse<>(errorCode.code(), resolvedMessage, null, TraceIds.current(), Instant.now());
    }

    public boolean successful() {
        return SUCCESS_CODE.equals(code);
    }
}
