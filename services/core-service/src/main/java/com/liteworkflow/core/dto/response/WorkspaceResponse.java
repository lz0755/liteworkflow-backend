package com.liteworkflow.core.dto.response;

import com.liteworkflow.core.domain.Workspace;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.domain.WorkspaceStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        String description,
        WorkspaceStatus status,
        WorkspaceRole currentUserRole,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkspaceResponse from(Workspace workspace, WorkspaceRole role) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getStatus(),
                role,
                workspace.getCreatedBy(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }
}
