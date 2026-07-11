package com.liteworkflow.core.dto.response;

import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        ProjectStatus status,
        ProjectRole currentUserRole,
        boolean workspaceAdmin,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static ProjectResponse from(Project project, ProjectRole role, boolean workspaceAdmin) {
        return new ProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                role,
                workspaceAdmin,
                project.getCreatedBy(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
