package com.liteworkflow.core.dto.response;

import java.util.UUID;

public record AiWeeklyReportContextResponse(
        UUID workspaceId,
        UUID projectId,
        String projectName,
        String projectDescription,
        String activityDigest) {
}
