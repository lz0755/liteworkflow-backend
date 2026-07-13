package com.liteworkflow.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.mq.event.EventHeaders;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

class CoreMqContractTest {

    @Test
    void serializedEventEnvelopeVersionPassesConsumerContract() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(
                UUID.randomUUID(), "issue.updated", 1, Instant.parse("2026-07-12T00:00:00Z"),
                new EventScope(workspaceId, projectId, UUID.randomUUID()), issueId,
                Map.of("sourceVersion", 2), Map.of());
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(envelope))
                .setHeader(EventHeaders.EVENT_TYPE, envelope.eventType())
                .setHeader(EventHeaders.EVENT_VERSION, envelope.version())
                .setRedelivered(false)
                .build();
        RagSourceEvent source = new RagSourceEvent(
                envelope.eventId(), workspaceId, projectId, RagSourceType.ISSUE,
                issueId, 2, "Issue", List.of("issue content"), false);
        CoreRagEventMapper mapper = mock(CoreRagEventMapper.class);
        when(mapper.map(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(source));
        RagIndexService index = mock(RagIndexService.class);

        new RagIndexEventConsumer(json, mapper, mock(ProjectDocumentExtractor.class), index).consume(message);

        ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> parsed =
                ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(mapper).map(parsed.capture());
        assertThat(parsed.getValue().path("version").asInt()).isEqualTo(1);
        verify(index).index(source, false);
    }

    @Test
    void brokerRedeliveryFlagIsForwardedToIndexClaim() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(
                UUID.randomUUID(), "issue.updated", 1, Instant.now(),
                new EventScope(workspaceId, projectId, UUID.randomUUID()), issueId,
                Map.of("sourceVersion", 2), Map.of());
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(envelope))
                .setHeader(EventHeaders.EVENT_TYPE, envelope.eventType())
                .setHeader(EventHeaders.EVENT_VERSION, envelope.version())
                .setRedelivered(true)
                .build();
        RagSourceEvent source = new RagSourceEvent(
                envelope.eventId(), workspaceId, projectId, RagSourceType.ISSUE,
                issueId, 2, "Issue", List.of("issue content"), false);
        CoreRagEventMapper mapper = mock(CoreRagEventMapper.class);
        when(mapper.map(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(source));
        RagIndexService index = mock(RagIndexService.class);

        new RagIndexEventConsumer(json, mapper, mock(ProjectDocumentExtractor.class), index).consume(message);

        verify(index).index(eq(source), eq(true));
    }
}
