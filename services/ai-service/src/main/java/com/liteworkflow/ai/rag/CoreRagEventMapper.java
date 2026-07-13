package com.liteworkflow.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CoreRagEventMapper {

    private final CoreRagSourceClient core;

    public CoreRagEventMapper(CoreRagSourceClient core) {
        this.core = core;
    }

    public List<RagSourceEvent> map(JsonNode root) {
        UUID eventId = uuid(root, "eventId");
        String eventType = text(root, "eventType");
        UUID workspaceId = uuid(root.path("scope"), "workspaceId");
        UUID projectId = uuid(root.path("scope"), "projectId");
        UUID sourceId = uuid(root, "aggregateId");
        if (eventType.startsWith("issue.")) {
            List<RagSourceEvent> events = new ArrayList<>();
            events.add(sourceEvent(eventId, workspaceId, projectId, sourceId,
                    RagSourceType.ISSUE, core.issue(sourceId)));
            if ("issue.deleted".equals(eventType)) {
                for (CoreRagSourceClient.Source comment : core.deletedIssueComments(sourceId)) {
                    UUID tombstoneEventId = UUID.nameUUIDFromBytes(
                            (eventId + ":" + comment.sourceId()).getBytes(StandardCharsets.UTF_8));
                    events.add(sourceEvent(tombstoneEventId, workspaceId, projectId, comment.sourceId(),
                            RagSourceType.COMMENT, comment));
                }
            }
            return List.copyOf(events);
        }
        if ("comment.created".equals(eventType)
                || "comment.updated".equals(eventType)
                || "comment.deleted".equals(eventType)) {
            return List.of(sourceEvent(eventId, workspaceId, projectId, sourceId,
                    RagSourceType.COMMENT, core.comment(sourceId)));
        }
        throw new IllegalArgumentException("Unsupported RAG event type " + eventType);
    }

    private static RagSourceEvent sourceEvent(
            UUID eventId,
            UUID expectedWorkspaceId,
            UUID expectedProjectId,
            UUID expectedSourceId,
            RagSourceType type,
            CoreRagSourceClient.Source source) {
        if (!expectedWorkspaceId.equals(source.workspaceId())
                || !expectedProjectId.equals(source.projectId())
                || !expectedSourceId.equals(source.sourceId())) {
            throw new IllegalArgumentException("Core RAG source scope does not match event scope");
        }
        List<String> chunks = source.deleted() ? List.of() : List.of(source.text());
        return new RagSourceEvent(eventId, source.workspaceId(), source.projectId(), type,
                source.sourceId(), source.sourceVersion(), source.title(), chunks, source.deleted());
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException(field + " is invalid"); }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText();
    }

}
