package com.liteworkflow.common.core.error;

import java.util.Objects;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message == null || message.isBlank() ? errorCode.defaultMessage() : message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message == null || message.isBlank() ? errorCode.defaultMessage() : message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
