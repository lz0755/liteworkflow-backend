package com.liteworkflow.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.mq.event.EventHeaders;
import com.liteworkflow.common.mq.event.JsonEventMessageFactory;
import com.liteworkflow.common.mq.event.ProjectDocumentEvent;
import com.liteworkflow.common.file.storage.ObjectContent;
import com.liteworkflow.common.file.storage.ObjectMetadata;
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.ai.config.RagProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

class DocumentMqContractTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void fileOutboxMessageIsConsumedAsDocumentUpsertUsingCanonicalHeaders() throws Exception {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        ProjectDocumentEvent payload = new ProjectDocumentEvent(
                eventId, "rag.document.upsert", 1, now, workspaceId, projectId,
                documentId, fileId, 2, UUID.randomUUID(), "rag-documents/safe.md",
                "safe.md", "text/markdown", 10, "a".repeat(64));
        Message sent = JsonEventMessageFactory.create(
                json.writeValueAsBytes(payload), eventId, payload.eventType(), payload.eventVersion(), "trace-id");
        assertThat(sent.getMessageProperties().getHeader(EventHeaders.EVENT_TYPE).toString())
                .isEqualTo("rag.document.upsert");
        assertThat((Number) sent.getMessageProperties().getHeader(EventHeaders.EVENT_VERSION))
                .extracting(Number::intValue).isEqualTo(1);

        ObjectStorage storage = mock(ObjectStorage.class);
        byte[] documentBody = "document body".getBytes();
        when(storage.get("rag-documents/safe.md")).thenAnswer(ignored -> new ObjectContent(
                new ObjectMetadata("rag-documents/safe.md", documentBody.length, "text/markdown",
                        "etag", now, Map.of()),
                new ByteArrayInputStream(documentBody)));
        RagProperties properties = new RagProperties();
        ProjectDocumentExtractor extractor = new ProjectDocumentExtractor(
                storage, new DocumentTokenChunker(properties), properties);
        RagIndexService index = mock(RagIndexService.class);
        RagIndexEventConsumer consumer = new RagIndexEventConsumer(
                json, mock(CoreRagEventMapper.class), extractor, index);
        consumer.consume(sent);

        ArgumentCaptor<RagSourceEvent> indexed = ArgumentCaptor.forClass(RagSourceEvent.class);
        verify(index).index(indexed.capture(), org.mockito.ArgumentMatchers.eq(false));
        assertThat(indexed.getValue().sourceId()).isEqualTo(documentId);
        assertThat(indexed.getValue().sourceVersion()).isEqualTo(2);
        assertThat(indexed.getValue().deleted()).isFalse();
        assertThat(indexed.getValue().chunks()).containsExactly("document body");
    }

    @Test
    void rejectsUnsupportedHeaderVersionBeforeParsingPayload() {
        Message message = MessageBuilder.withBody("{}".getBytes())
                .setHeader(EventHeaders.EVENT_TYPE, "rag.document.upsert")
                .setHeader(EventHeaders.EVENT_VERSION, 2)
                .build();
        RagIndexEventConsumer consumer = new RagIndexEventConsumer(
                json, mock(CoreRagEventMapper.class), mock(ProjectDocumentExtractor.class),
                mock(RagIndexService.class));

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported RAG event version 2");
    }

    @Test
    void documentDeleteCreatesTombstoneWithoutReadingDeletedObject() throws Exception {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        UUID documentId = UUID.randomUUID();
        ProjectDocumentEvent payload = new ProjectDocumentEvent(
                UUID.randomUUID(), "rag.document.deleted", 1, now,
                UUID.randomUUID(), UUID.randomUUID(), documentId, UUID.randomUUID(), 3,
                UUID.randomUUID(), null, "safe.md", null, 0, null);
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(payload))
                .setHeader(EventHeaders.EVENT_TYPE, payload.eventType())
                .setHeader(EventHeaders.EVENT_VERSION, payload.eventVersion())
                .build();
        ProjectDocumentExtractor extractor = mock(ProjectDocumentExtractor.class);
        RagIndexService index = mock(RagIndexService.class);
        new RagIndexEventConsumer(json, mock(CoreRagEventMapper.class), extractor, index).consume(message);

        org.mockito.Mockito.verifyNoInteractions(extractor);
        ArgumentCaptor<RagSourceEvent> indexed = ArgumentCaptor.forClass(RagSourceEvent.class);
        verify(index).index(indexed.capture(), org.mockito.ArgumentMatchers.eq(false));
        assertThat(indexed.getValue().sourceId()).isEqualTo(documentId);
        assertThat(indexed.getValue().sourceVersion()).isEqualTo(3);
        assertThat(indexed.getValue().deleted()).isTrue();
        assertThat(indexed.getValue().chunks()).isEmpty();
    }
}
