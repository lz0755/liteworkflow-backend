package com.liteworkflow.core.application;

import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.WorkspaceRole;
import java.util.UUID;

public interface PermissionService {

    void requireActiveUser(UUID userId);

    WorkspaceRole requireWorkspaceMember(UUID workspaceId, UUID userId);

    WorkspaceRole requireWorkspaceRole(UUID workspaceId, UUID userId, WorkspaceRole... roles);

    default WorkspaceRole requireWorkspaceMemberManager(UUID workspaceId, UUID userId) {
        return requireWorkspaceRole(workspaceId, userId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    }

    ProjectRole requireProjectMember(UUID projectId, UUID userId);

    ProjectRole requireProjectRole(UUID projectId, UUID userId, ProjectRole... roles);

    default ProjectRole requireProjectMemberManager(UUID projectId, UUID userId) {
        return requireProjectRole(projectId, userId, ProjectRole.PROJECT_ADMIN);
    }

    default ProjectRole requireIssueWriter(UUID projectId, UUID userId) {
        return requireProjectRole(projectId, userId, ProjectRole.PROJECT_ADMIN, ProjectRole.MEMBER);
    }

    boolean canReadProject(UUID projectId, UUID userId);

    boolean canManageProjectMembers(UUID projectId, UUID userId);
}
