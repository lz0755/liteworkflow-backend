package com.liteworkflow.ai.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagIndexService {

    private static final Logger log = LoggerFactory.getLogger(RagIndexService.class);
    private final VectorStore vectorStore;
    private final RagIndexStore indexStore;

    public RagIndexService(VectorStore vectorStore, RagIndexStore indexStore) {
        this.vectorStore = vectorStore;
        this.indexStore = indexStore;
    }

    public Result index(RagSourceEvent event) {
        return index(event, false);
    }

    public Result index(RagSourceEvent event, boolean redelivered) {
        RagIndexStore.Claim claim = indexStore.claim(event, redelivered);
        if (claim == RagIndexStore.Claim.DUPLICATE) return Result.DUPLICATE;
        if (claim == RagIndexStore.Claim.STALE) return Result.STALE;
        if (claim == RagIndexStore.Claim.EXHAUSTED) {
            throw new AmqpRejectAndDontRequeueException("RAG index attempts exhausted");
        }

        List<UUID> vectorIds = vectorIds(event);
        try {
            if (!event.deleted()) vectorStore.add(documents(event, vectorIds));
            RagIndexStore.FinalizeResult result = indexStore.finalizeVersion(event, vectorIds);
            log.info("RAG source indexed eventId={} sourceType={} sourceId={} sourceVersion={} chunks={} result={}",
                    event.eventId(), event.sourceType(), event.sourceId(), event.sourceVersion(),
                    vectorIds.size(), result);
            return result == RagIndexStore.FinalizeResult.ACTIVATED ? Result.ACTIVATED : Result.STALE;
        } catch (RuntimeException failure) {
            indexStore.markFailed(event.eventId(), failure);
            log.warn("RAG source indexing failed eventId={} sourceType={} sourceId={} sourceVersion={}",
                    event.eventId(), event.sourceType(), event.sourceId(), event.sourceVersion());
            throw failure;
        }
    }

    private static List<Document> documents(RagSourceEvent event, List<UUID> vectorIds) {
        List<Document> documents = new ArrayList<>(event.chunks().size());
        for (int index = 0; index < event.chunks().size(); index++) {
            Map<String, Object> metadata = Map.of(
                    "workspaceId", event.workspaceId().toString(),
                    "projectId", event.projectId().toString(),
                    "sourceType", event.sourceType().name(),
                    "sourceId", event.sourceId().toString(),
                    "sourceVersion", event.sourceVersion(),
                    "chunkIndex", index,
                    "title", event.title(),
                    "active", false);
            documents.add(Document.builder()
                    .id(vectorIds.get(index).toString())
                    .text(event.chunks().get(index))
                    .metadata(metadata)
                    .build());
        }
        return List.copyOf(documents);
    }

    private static List<UUID> vectorIds(RagSourceEvent event) {
        List<UUID> ids = new ArrayList<>(event.chunks().size());
        for (int index = 0; index < event.chunks().size(); index++) {
            String key = event.sourceType() + ":" + event.sourceId() + ":"
                    + event.sourceVersion() + ":" + index;
            ids.add(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        }
        return List.copyOf(ids);
    }

    public enum Result { ACTIVATED, DUPLICATE, STALE }
}
