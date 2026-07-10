package com.liteworkflow.common.file.storage;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ObjectMetadata(
        String objectKey,
        long contentLength,
        String contentType,
        String etag,
        Instant lastModified,
        Map<String, String> metadata) {

    public ObjectMetadata {
        objectKey = ObjectKeys.requireSafe(objectKey);
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        Objects.requireNonNull(contentType, "contentType must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
