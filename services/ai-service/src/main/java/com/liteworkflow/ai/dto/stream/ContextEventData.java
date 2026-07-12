package com.liteworkflow.ai.dto.stream;

import java.util.List;
import java.util.UUID;

public record ContextEventData(
        UUID requestId,
        UUID conversationId,
        UUID workspaceId,
        UUID projectId,
        List<ContextSourceData> sources) implements AiStreamEventData {

    public ContextEventData {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
