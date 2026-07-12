package com.liteworkflow.infra.notification;

import com.liteworkflow.common.core.error.ErrorCode;

public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_NOT_FOUND("INFRA_NOTIFICATION_NOT_FOUND", "Notification not found", 404);

    private final String code;
    private final String message;
    private final int httpStatus;

    NotificationErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
