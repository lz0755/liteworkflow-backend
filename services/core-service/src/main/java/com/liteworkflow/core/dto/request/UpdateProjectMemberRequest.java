package com.liteworkflow.core.dto.request;

import com.liteworkflow.core.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRequest(@NotNull ProjectRole role) {
}
