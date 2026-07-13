package com.liteworkflow.ai.rag;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RagSourceEvent(
        UUID eventId,
        UUID workspaceId,
        UUID projectId,
        RagSourceType sourceType,
        UUID sourceId,
        long sourceVersion,
        String title,
        List<String> chunks,
        boolean deleted) {

    public RagSourceEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        if (sourceVersion < 0) throw new IllegalArgumentException("sourceVersion must not be negative");
        title = title == null || title.isBlank() ? sourceType + " " + sourceId : title.strip();
        chunks = chunks == null ? List.of() : chunks.stream()
                .filter(Objects::nonNull).filter(value -> !value.isBlank()).toList();
        if (!deleted && chunks.isEmpty()) throw new IllegalArgumentException("active source needs content");
        if (deleted && !chunks.isEmpty()) throw new IllegalArgumentException("deleted source cannot have content");
    }
}
