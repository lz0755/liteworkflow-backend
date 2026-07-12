package com.liteworkflow.ai.dto.response;

import com.liteworkflow.ai.domain.AiConversation;
import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String operation,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static ConversationSummaryResponse from(AiConversation conversation) {
        return new ConversationSummaryResponse(
                conversation.id(), conversation.workspaceId(), conversation.projectId(),
                conversation.operation(), conversation.title(), conversation.status(),
                conversation.createdAt(), conversation.updatedAt());
    }
}
