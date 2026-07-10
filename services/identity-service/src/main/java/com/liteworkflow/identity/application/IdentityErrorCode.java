package com.liteworkflow.identity.application;

import com.liteworkflow.common.core.error.ErrorCode;

public enum IdentityErrorCode implements ErrorCode {
    EMAIL_ALREADY_REGISTERED("IDENTITY_EMAIL_EXISTS", "This email address is already registered", 409),
    AUTHENTICATION_FAILED("IDENTITY_AUTH_FAILED", "Invalid email or password", 401),
    REFRESH_TOKEN_INVALID("IDENTITY_REFRESH_INVALID", "Refresh token is invalid or expired", 401),
    RESET_TOKEN_INVALID("IDENTITY_RESET_INVALID", "Reset token is invalid or expired", 400),
    USER_NOT_FOUND("IDENTITY_USER_NOT_FOUND", "User was not found", 404),
    USER_DISABLED("IDENTITY_USER_DISABLED", "User account is disabled", 403),
    CURRENT_PASSWORD_INVALID("IDENTITY_CURRENT_PASSWORD_INVALID", "Current password is invalid", 400);

    private final String code;
    private final String message;
    private final int status;

    IdentityErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
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
        return status;
    }
}
