package com.liteworkflow.common.file.storage;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

public record PutObjectRequest(
        String objectKey,
        InputStream content,
        long contentLength,
        String contentType,
        Map<String, String> metadata) {

    public PutObjectRequest {
        objectKey = ObjectKeys.requireSafe(objectKey);
        Objects.requireNonNull(content, "content must not be null");
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
