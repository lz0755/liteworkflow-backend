package com.liteworkflow.core.domain;

public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER;

    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }
}
