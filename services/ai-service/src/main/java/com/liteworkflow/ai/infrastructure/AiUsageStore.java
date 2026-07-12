package com.liteworkflow.ai.infrastructure;

import com.liteworkflow.ai.domain.AiTokenUsage;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiUsageStore {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiUsageStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(
            UUID requestId,
            String traceId,
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            UUID conversationId,
            String operation,
            String provider,
            String chatModel,
            AiTokenUsage usage,
            long latencyMs,
            boolean success,
            String errorCode) {
        jdbc.update("""
                INSERT INTO ai.ai_usage_logs
                    (request_id, trace_id, user_id, workspace_id, project_id, conversation_id,
                     operation, provider, chat_model, input_tokens, output_tokens, total_tokens,
                     latency_ms, success, error_code, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                traceId,
                userId,
                workspaceId,
                projectId,
                conversationId,
                operation,
                provider,
                chatModel,
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                Math.max(0, latencyMs),
                success,
                errorCode,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
