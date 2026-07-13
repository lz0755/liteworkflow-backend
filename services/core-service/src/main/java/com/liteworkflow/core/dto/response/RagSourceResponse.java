package com.liteworkflow.core.dto.response;

import java.util.UUID;

public record RagSourceResponse(
        UUID workspaceId,
        UUID projectId,
        UUID sourceId,
        long sourceVersion,
        boolean deleted,
        String title,
        String text) {
}
