package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChangeIssueStateRequest(@NotNull UUID stateId) {
}
