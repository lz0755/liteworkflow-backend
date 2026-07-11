package com.liteworkflow.core.dto.request;

import com.liteworkflow.core.domain.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddWorkspaceMemberRequest(
        @NotNull UUID userId,
        @NotNull WorkspaceRole role) {
}
