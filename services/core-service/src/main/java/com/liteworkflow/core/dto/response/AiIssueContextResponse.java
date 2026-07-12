package com.liteworkflow.core.dto.response;

import java.util.UUID;

public record AiIssueContextResponse(
        UUID workspaceId,
        UUID projectId,
        UUID issueId,
        String title,
        String description,
        String activityDigest) {
}
