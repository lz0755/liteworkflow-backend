package com.liteworkflow.core.dto.response;

import java.util.UUID;

public record AiProjectContextResponse(
        UUID workspaceId,
        UUID projectId,
        String name,
        String description) {
}
