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
    PROJECT_NOT_FOUND("CORE_PROJECT_404", "Project was not found", 404),
    PROJECT_PERMISSION_DENIED("CORE_PROJECT_403", "Project permission is denied", 403),
    PROJECT_MEMBER_ALREADY_EXISTS("CORE_PROJECT_MEMBER_409", "Project member already exists", 409),
    PROJECT_MEMBER_NOT_FOUND("CORE_PROJECT_MEMBER_404", "Project member was not found", 404),
    PROJECT_MEMBER_REQUIRES_WORKSPACE_MEMBER(
            "CORE_PROJECT_MEMBER_WORKSPACE_409", "Project member must be an active workspace member", 409),
    PROJECT_MEMBER_PERMISSION_DENIED(
            "CORE_PROJECT_MEMBER_403", "Project member permission is denied", 403),
    PROJECT_LAST_ADMIN_REQUIRED(
            "CORE_PROJECT_ADMIN_409", "Project must retain at least one project admin", 409),
    ISSUE_NOT_FOUND("CORE_ISSUE_404", "Issue was not found", 404),
    ISSUE_STATE_NOT_FOUND("CORE_ISSUE_STATE_404", "Issue state was not found", 404),
    ISSUE_STATE_INVALID("CORE_ISSUE_STATE_400", "Issue state is invalid for this project", 400),
    ISSUE_STATE_IN_USE("CORE_ISSUE_STATE_409", "Issue state is in use", 409),
    ISSUE_DEFAULT_STATE_REQUIRED("CORE_ISSUE_DEFAULT_STATE_409", "Project must retain a default issue state", 409),
    ISSUE_LABEL_NOT_FOUND("CORE_ISSUE_LABEL_404", "Issue label was not found", 404),
    ISSUE_LABEL_ALREADY_EXISTS("CORE_ISSUE_LABEL_409", "Issue label already exists", 409),
    ISSUE_ASSIGNEE_NOT_ELIGIBLE(
            "CORE_ISSUE_ASSIGNEE_409", "Assignee must be an active project member or workspace manager", 409),
    ISSUE_IDEMPOTENCY_CONFLICT(
            "CORE_ISSUE_IDEMPOTENCY_409", "Idempotency key was already used with different content", 409),
    COMMENT_NOT_FOUND("CORE_COMMENT_404", "Comment was not found", 404),
    COMMENT_MODIFICATION_DENIED(
            "CORE_COMMENT_403", "Only the comment author or a project administrator may modify it", 403),
    COMMENT_BODY_INVALID("CORE_COMMENT_BODY_400", "Comment body must not be blank", 400),
    MENTION_USER_NOT_FOUND("CORE_MENTION_USER_404", "Mentioned user was not found", 404),
    MENTION_USER_NOT_ELIGIBLE(
            "CORE_MENTION_MEMBER_409", "Mentioned user must be an active member of this project", 409),
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
