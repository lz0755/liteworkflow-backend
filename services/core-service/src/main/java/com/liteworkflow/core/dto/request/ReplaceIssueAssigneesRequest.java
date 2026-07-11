package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record ReplaceIssueAssigneesRequest(@NotNull Set<UUID> assigneeIds) {
}
