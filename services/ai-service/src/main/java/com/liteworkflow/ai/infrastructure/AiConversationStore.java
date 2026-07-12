package com.liteworkflow.ai.infrastructure;

import com.liteworkflow.ai.domain.AiConversation;
import com.liteworkflow.ai.domain.AiMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiConversationStore {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiConversationStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public AiConversation create(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            String operation,
            String title) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO ai.ai_conversations
                    (id, user_id, workspace_id, project_id, operation, title, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, id, userId, workspaceId, projectId, operation, title, time(now), time(now));
        return new AiConversation(id, userId, workspaceId, projectId, operation, title, "ACTIVE", now, now);
    }

    public void touch(UUID conversationId) {
        jdbc.update("UPDATE ai.ai_conversations SET updated_at = ? WHERE id = ?", time(clock.instant()), conversationId);
    }

    public AiMessage addMessage(UUID conversationId, String role, String content, int tokenCount) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO ai.ai_messages (id, conversation_id, role, content, token_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, conversationId, role, content, Math.max(0, tokenCount), time(now));
        touch(conversationId);
        return new AiMessage(id, conversationId, role, content, Math.max(0, tokenCount), now);
    }

    public Optional<AiConversation> findOwned(UUID conversationId, UUID userId) {
        return jdbc.query("""
                SELECT id, user_id, workspace_id, project_id, operation, title, status, created_at, updated_at
                FROM ai.ai_conversations WHERE id = ? AND user_id = ?
                """, AiConversationStore::conversation, conversationId, userId).stream().findFirst();
    }

    public boolean exists(UUID conversationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai.ai_conversations WHERE id = ?", Integer.class, conversationId);
        return count != null && count > 0;
    }

    public List<AiConversation> listOwned(UUID userId, int limit, int offset) {
        return jdbc.query("""
                SELECT id, user_id, workspace_id, project_id, operation, title, status, created_at, updated_at
                FROM ai.ai_conversations
                WHERE user_id = ?
                ORDER BY updated_at DESC, id
                LIMIT ? OFFSET ?
                """, AiConversationStore::conversation, userId, limit, offset);
    }

    public long countOwned(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai.ai_conversations WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    public List<AiMessage> messages(UUID conversationId, int limit) {
        return jdbc.query("""
                SELECT id, conversation_id, role, content, token_count, created_at
                FROM (
                    SELECT id, conversation_id, role, content, token_count, created_at
                    FROM ai.ai_messages
                    WHERE conversation_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                ) recent
                ORDER BY created_at, id
                """, AiConversationStore::message, conversationId, limit);
    }

    private static AiConversation conversation(ResultSet result, int row) throws SQLException {
        return new AiConversation(
                result.getObject("id", UUID.class),
                result.getObject("user_id", UUID.class),
                result.getObject("workspace_id", UUID.class),
                result.getObject("project_id", UUID.class),
                result.getString("operation"),
                result.getString("title"),
                result.getString("status"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static AiMessage message(ResultSet result, int row) throws SQLException {
        return new AiMessage(
                result.getObject("id", UUID.class),
                result.getObject("conversation_id", UUID.class),
                result.getString("role"),
                result.getString("content"),
                result.getInt("token_count"),
                instant(result, "created_at"));
    }

    private static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }
}
