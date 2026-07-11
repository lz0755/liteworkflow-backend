package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.ErrorCode;

public enum CoreErrorCode implements ErrorCode {
    USER_NOT_FOUND("CORE_USER_404", "User was not found", 404),
    USER_NOT_ACTIVE("CORE_USER_409", "User is not active", 409),
    USER_SEARCH_KEYWORD_TOO_SHORT("CORE_USER_SEARCH_400", "Search keyword is too short", 400),
    USER_SEARCH_CONTEXT_UNSUPPORTED("CORE_USER_SEARCH_CONTEXT_400", "Search context is not supported", 400),
    WORKSPACE_NOT_FOUND("CORE_WORKSPACE_404", "Workspace was not found", 404),
    WORKSPACE_PERMISSION_DENIED("CORE_WORKSPACE_403", "Workspace permission is denied", 403),
    WORKSPACE_MEMBER_ALREADY_EXISTS("CORE_WORKSPACE_MEMBER_409", "Workspace member already exists", 409),
    WORKSPACE_MEMBER_NOT_FOUND("CORE_WORKSPACE_MEMBER_404", "Workspace member was not found", 404),
    WORKSPACE_LAST_OWNER_REQUIRED("CORE_WORKSPACE_OWNER_409", "Workspace must retain at least one owner", 409),
    WORKSPACE_MEMBER_PERMISSION_DENIED(
            "CORE_WORKSPACE_MEMBER_403", "Workspace member permission is denied", 403),
    INVALID_WORKSPACE_ROLE("CORE_WORKSPACE_ROLE_400", "Workspace role is invalid", 400),
    INVALID_PROFILE("CORE_PROFILE_400", "User profile is invalid", 400);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    CoreErrorCode(String code, String defaultMessage, int httpStatus) {
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
