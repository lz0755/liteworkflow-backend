package com.liteworkflow.infra.file;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(UUID id, UUID documentId, long sourceVersion,
        FilePurpose purpose, FileScope scope, UUID scopeId,
        String originalName, String contentType, long sizeBytes, String sha256Hex,
        UUID createdBy, Instant createdAt) {
    static FileResponse from(StoredFile file) {
        return new FileResponse(file.getId(), file.getDocumentId(), file.getSourceVersion(),
                file.getPurpose(), file.getScopeType(), file.getScopeId(),
                file.getOriginalName(), file.getContentType(), file.getSizeBytes(), file.getSha256Hex(),
                file.getCreatedBy(), file.getCreatedAt());
    }
}
