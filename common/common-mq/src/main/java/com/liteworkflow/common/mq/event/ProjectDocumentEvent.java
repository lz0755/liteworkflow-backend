package com.liteworkflow.common.mq.event;

import java.time.Instant;
import java.util.UUID;

/** Metadata-only RAG document event shared by the file producer and AI consumer. */
public record ProjectDocumentEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID workspaceId,
        UUID projectId,
        UUID documentId,
        UUID fileId,
        long sourceVersion,
        UUID actorId,
        String objectKey,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256Hex) {
}
