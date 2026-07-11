package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record ReplaceIssueLabelsRequest(@NotNull Set<UUID> labelIds) {
}
