package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreateIssueRequest(
        @NotBlank @Size(max = 240) String title,
        @Size(max = 20000) String description,
        UUID stateId,
        Set<UUID> assigneeIds,
        Set<UUID> labelIds,
        UUID clientRequestId) {
}
