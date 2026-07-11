package com.liteworkflow.core.application;

import com.liteworkflow.core.domain.WorkspaceRole;
import java.util.UUID;

public interface PermissionService {

    void requireActiveUser(UUID userId);

    WorkspaceRole requireWorkspaceMember(UUID workspaceId, UUID userId);

    WorkspaceRole requireWorkspaceRole(UUID workspaceId, UUID userId, WorkspaceRole... roles);

    default WorkspaceRole requireWorkspaceMemberManager(UUID workspaceId, UUID userId) {
        return requireWorkspaceRole(workspaceId, userId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    }
}
