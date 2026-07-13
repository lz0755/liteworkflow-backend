package com.liteworkflow.ai.rag;

import com.liteworkflow.ai.config.RagProperties;
import com.liteworkflow.ai.dto.response.ProjectAskSource;
import com.liteworkflow.ai.application.AiErrorCode;
import com.liteworkflow.common.core.error.BizException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagRetrievalService {

    private final VectorStore vectorStore;
    private final RagProperties properties;

    public RagRetrievalService(VectorStore vectorStore, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public Retrieval retrieve(UUID workspaceId, UUID projectId, String question) {
        String filter = "workspaceId == '" + workspaceId + "' && projectId == '" + projectId
                + "' && active == true";
        SearchRequest request = SearchRequest.builder()
                .query(question.strip())
                .topK(properties.getTopK())
                .similarityThreshold(properties.getSimilarityThreshold())
                .filterExpression(filter)
                .build();
        List<Document> documents;
        try {
            documents = List.copyOf(vectorStore.similaritySearch(request));
        } catch (RuntimeException exception) {
            throw new BizException(AiErrorCode.PROVIDER_UNAVAILABLE,
                    "Embedding provider is unavailable for project search", exception);
        }
        return new Retrieval(documents, documents.stream().map(RagRetrievalService::source).toList());
    }

    private static ProjectAskSource source(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new ProjectAskSource(
                string(metadata, "sourceType"),
                UUID.fromString(string(metadata, "sourceId")),
                number(metadata, "sourceVersion").longValue(),
                number(metadata, "chunkIndex").intValue(),
                string(metadata, "title"),
                document.getScore() == null ? 0.0 : document.getScore());
    }

    private static String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("RAG result metadata is missing " + key);
        }
        return value.toString();
    }

    private static Number number(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) return number;
        try { return Long.parseLong(string(metadata, key)); }
        catch (NumberFormatException exception) {
            throw new IllegalStateException("RAG result metadata is invalid " + key, exception);
        }
    }

    public record Retrieval(List<Document> documents, List<ProjectAskSource> sources) {
        public Retrieval {
            documents = List.copyOf(documents);
            sources = List.copyOf(sources);
        }
    }
}
