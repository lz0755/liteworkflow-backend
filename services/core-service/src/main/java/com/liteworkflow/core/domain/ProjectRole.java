package com.liteworkflow.core.domain;

public enum ProjectRole {
    PROJECT_ADMIN,
    MEMBER,
    VIEWER;

    public boolean canManageMembers() {
        return this == PROJECT_ADMIN;
    }
}
