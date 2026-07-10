package com.liteworkflow.common.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record ObjectContent(ObjectMetadata metadata, InputStream content) implements AutoCloseable {

    public ObjectContent {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
