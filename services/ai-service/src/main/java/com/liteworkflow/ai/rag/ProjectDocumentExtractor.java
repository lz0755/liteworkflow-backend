package com.liteworkflow.ai.rag;

import com.liteworkflow.ai.config.RagProperties;
import com.liteworkflow.common.file.storage.ObjectContent;
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.mq.event.ProjectDocumentEvent;
import java.io.IOException;
import java.util.List;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProjectDocumentExtractor {

    private final ObjectStorage storage;
    private final DocumentTokenChunker chunker;
    private final RagProperties properties;

    public ProjectDocumentExtractor(
            ObjectStorage storage, DocumentTokenChunker chunker, RagProperties properties) {
        this.storage = storage;
        this.chunker = chunker;
        this.properties = properties;
    }

    public List<String> extract(ProjectDocumentEvent event) {
        validate(event);
        try (ObjectContent object = storage.get(event.objectKey())) {
            byte[] bytes = object.content().readNBytes(Math.toIntExact(properties.getMaxDocumentBytes() + 1));
            if (bytes.length > properties.getMaxDocumentBytes()) {
                throw new IllegalArgumentException("Project document exceeds RAG extraction limit");
            }
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override public String getFilename() { return event.originalName(); }
            };
            String extracted = new TikaDocumentReader(resource).get().stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .filter(java.util.Objects::nonNull)
                    .reduce("", (left, right) -> left + "\n" + right).strip();
            if (extracted.length() > properties.getMaxExtractedCharacters()) {
                throw new IllegalArgumentException("Extracted project document text is too large");
            }
            List<String> chunks = chunker.split(extracted);
            if (chunks.isEmpty()) throw new IllegalArgumentException("Project document contains no extractable text");
            return chunks;
        } catch (IOException exception) {
            throw new IllegalStateException("Project document could not be read", exception);
        }
    }

    private void validate(ProjectDocumentEvent event) {
        if (event == null || event.eventId() == null || event.workspaceId() == null
                || event.projectId() == null || event.documentId() == null || event.fileId() == null
                || event.sourceVersion() < 1
                || event.objectKey() == null || event.objectKey().isBlank()
                || event.originalName() == null || event.originalName().isBlank()
                || event.sizeBytes() < 0 || event.sizeBytes() > properties.getMaxDocumentBytes()) {
            throw new IllegalArgumentException("Invalid project document index event");
        }
        if (!"rag.document.upsert".equals(event.eventType()) || event.eventVersion() != 1) {
            throw new IllegalArgumentException("Unsupported project document index event");
        }
    }
}
