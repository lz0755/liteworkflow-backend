package com.liteworkflow.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.mq.event.EventHeaders;
import com.liteworkflow.common.mq.event.ProjectDocumentEvent;
import java.io.IOException;
import java.util.List;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagIndexEventConsumer {

    private final ObjectMapper objectMapper;
    private final CoreRagEventMapper coreMapper;
    private final ProjectDocumentExtractor documentExtractor;
    private final RagIndexService indexService;

    public RagIndexEventConsumer(
            ObjectMapper objectMapper,
            CoreRagEventMapper coreMapper,
            ProjectDocumentExtractor documentExtractor,
            RagIndexService indexService) {
        this.objectMapper = objectMapper;
        this.coreMapper = coreMapper;
        this.documentExtractor = documentExtractor;
        this.indexService = indexService;
    }

    @RabbitListener(
            queues = RagAmqpConfiguration.INDEX_QUEUE,
            containerFactory = "ragRabbitListenerContainerFactory")
    public void consume(Message message) {
        boolean redelivered = Boolean.TRUE.equals(message.getMessageProperties().isRedelivered());
        String eventType = requiredHeader(message, EventHeaders.EVENT_TYPE, String.class);
        int eventVersion = eventVersion(message);
        if (eventVersion != 1) {
            throw new IllegalArgumentException("Unsupported RAG event version " + eventVersion);
        }
        try {
            if ("rag.document.upsert".equals(eventType) || "rag.document.deleted".equals(eventType)) {
                ProjectDocumentEvent event = objectMapper.readValue(message.getBody(), ProjectDocumentEvent.class);
                if (!eventType.equals(event.eventType()) || event.eventVersion() != eventVersion) {
                    throw new IllegalArgumentException("RAG document event headers do not match its payload");
                }
                boolean deleted = "rag.document.deleted".equals(eventType);
                List<String> chunks = deleted ? List.of() : documentExtractor.extract(event);
                indexService.index(new RagSourceEvent(
                        event.eventId(), event.workspaceId(), event.projectId(), RagSourceType.DOCUMENT,
                        event.documentId(), event.sourceVersion(), event.originalName(), chunks, deleted), redelivered);
                return;
            }
            JsonNode event = objectMapper.readTree(message.getBody());
            if (!eventType.equals(event.path("eventType").asText())
                    || event.path("version").asInt(-1) != eventVersion) {
                throw new IllegalArgumentException("Core event headers do not match its payload");
            }
            for (RagSourceEvent source : coreMapper.map(event)) indexService.index(source, redelivered);
        } catch (IOException exception) {
            throw new IllegalArgumentException("RAG index event JSON is invalid", exception);
        }
    }

    private static int eventVersion(Message message) {
        Object value = message.getMessageProperties().getHeader(EventHeaders.EVENT_VERSION);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try { return Integer.parseInt(text); }
            catch (NumberFormatException ignored) { /* handled below */ }
        }
        throw new IllegalArgumentException("Missing or invalid " + EventHeaders.EVENT_VERSION + " header");
    }

    private static <T> T requiredHeader(Message message, String name, Class<T> type) {
        Object value = message.getMessageProperties().getHeader(name);
        if (!type.isInstance(value)) throw new IllegalArgumentException("Missing or invalid " + name + " header");
        return type.cast(value);
    }
}
