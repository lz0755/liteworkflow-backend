package com.liteworkflow.core.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID issueId,
        UUID authorId,
        String body,
        Set<UUID> mentionedUserIds,
        Instant createdAt,
        Instant updatedAt) {
}
