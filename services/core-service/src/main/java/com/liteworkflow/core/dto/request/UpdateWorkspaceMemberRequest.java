package com.liteworkflow.core.dto.request;

import com.liteworkflow.core.domain.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceMemberRequest(@NotNull WorkspaceRole role) {
}
