package com.liteworkflow.core.dto.request;

import com.liteworkflow.core.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddProjectMemberRequest(
        @NotNull UUID userId,
        @NotNull ProjectRole role) {
}
