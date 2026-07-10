package com.liteworkflow.common.ai;

import java.util.Objects;
import java.util.UUID;

/** Tenant scope for calls made through Spring AI abstractions. */
public record AiRequestContext(
        String requestId,
        UUID userId,
        UUID workspaceId,
        UUID projectId) {

    public AiRequestContext {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        Objects.requireNonNull(userId, "userId must not be null");
        if (projectId != null && workspaceId == null) {
            throw new IllegalArgumentException("project-scoped AI requests require a workspaceId");
        }
    }
}
