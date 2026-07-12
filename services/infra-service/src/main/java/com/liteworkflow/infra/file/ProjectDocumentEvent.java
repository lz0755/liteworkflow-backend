package com.liteworkflow.infra.file;

import java.time.Instant;
import java.util.UUID;

/** Metadata-only event. File bytes and extracted text must never be added to this contract. */
public record ProjectDocumentEvent(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
        UUID workspaceId, UUID projectId, UUID fileId, UUID actorId, String objectKey,
        String originalName, String contentType, long sizeBytes, String sha256Hex) {
}
