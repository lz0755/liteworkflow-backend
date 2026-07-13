package com.liteworkflow.ai.dto.response;

import java.util.List;
import java.util.UUID;

public record ProjectAskResponse(
        UUID requestId,
        UUID conversationId,
        String answer,
        List<ProjectAskSource> sources,
        AiUsageResponse usage) {
}
