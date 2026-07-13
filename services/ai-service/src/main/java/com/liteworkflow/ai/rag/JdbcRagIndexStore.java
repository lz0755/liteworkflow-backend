package com.liteworkflow.ai.rag;

import com.liteworkflow.ai.config.RagProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcRagIndexStore implements RagIndexStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final RagProperties properties;

    public JdbcRagIndexStore(
            JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock, RagProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public Claim claim(RagSourceEvent event, boolean redelivered) {
        return transactions.execute(status -> claimInTransaction(event, redelivered));
    }

    private Claim claimInTransaction(RagSourceEvent event, boolean redelivered) {
        OffsetDateTime claimedAt = now();
        OffsetDateTime leaseUntil = claimedAt.plus(properties.getIndexProcessingLease());
        Job existing = job(event.eventId());
        if (existing != null) {
            if ("COMPLETED".equals(existing.status())) {
                return Claim.DUPLICATE;
            }
            if ("DEAD".equals(existing.status())) return Claim.EXHAUSTED;
            boolean expiredProcessing = "PROCESSING".equals(existing.status())
                    && (existing.leaseUntil() == null || !existing.leaseUntil().isAfter(claimedAt));
            boolean processingTakeover = "PROCESSING".equals(existing.status())
                    && (expiredProcessing || redelivered);
            if ("PROCESSING".equals(existing.status()) && !processingTakeover) return Claim.DUPLICATE;
            int consumedAttempts = existing.retryCount() + (processingTakeover ? 1 : 0);
            if (consumedAttempts >= properties.getIndexMaxAttempts()) {
                exhaustJob(event.eventId());
                return Claim.EXHAUSTED;
            }
            jdbc.update("""
                    UPDATE rag.rag_index_jobs
                    SET status = 'PROCESSING', started_at = ?, lease_until = ?, last_error = NULL,
                        retry_count = retry_count + ?, updated_at = ?
                    WHERE event_id = ?
                    """, claimedAt, leaseUntil, processingTakeover ? 1 : 0, claimedAt, event.eventId());
        } else {
            try {
                jdbc.update("""
                        INSERT INTO rag.rag_index_jobs
                            (id, event_id, workspace_id, project_id, source_type, source_id,
                             source_version, status, retry_count, started_at, lease_until, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'PROCESSING', 0, ?, ?, ?, ?)
                        """, UUID.randomUUID(), event.eventId(), event.workspaceId(), event.projectId(),
                        event.sourceType().name(), event.sourceId(), event.sourceVersion(),
                        claimedAt, leaseUntil, claimedAt, claimedAt);
            } catch (DuplicateKeyException duplicate) {
                return Claim.DUPLICATE;
            }
        }

        Long currentVersion = jdbc.query("""
                SELECT source_version FROM rag.rag_source_heads
                WHERE source_type = ? AND source_id = ?
                FOR UPDATE
                """, (result, row) -> result.getLong(1), event.sourceType().name(), event.sourceId())
                .stream().findFirst().orElse(null);
        if (currentVersion != null && currentVersion >= event.sourceVersion()) {
            completeJob(event.eventId());
            return Claim.STALE;
        }
        return Claim.PROCESS;
    }

    @Override
    public FinalizeResult finalizeVersion(RagSourceEvent event, List<UUID> vectorIds) {
        return transactions.execute(status -> finalizeInTransaction(event, vectorIds));
    }

    private FinalizeResult finalizeInTransaction(RagSourceEvent event, List<UUID> vectorIds) {
        advisoryLock(event.sourceType(), event.sourceId());
        Head head = jdbc.query("""
                SELECT source_version, workspace_id, project_id
                FROM rag.rag_source_heads
                WHERE source_type = ? AND source_id = ?
                FOR UPDATE
                """, JdbcRagIndexStore::head, event.sourceType().name(), event.sourceId())
                .stream().findFirst().orElse(null);
        if (head != null && head.sourceVersion() >= event.sourceVersion()) {
            completeJob(event.eventId());
            return FinalizeResult.STALE;
        }

        jdbc.update("""
                UPDATE rag.vector_store
                SET metadata = jsonb_set(metadata::jsonb, '{active}', 'false'::jsonb)::json
                WHERE metadata->>'sourceType' = ? AND metadata->>'sourceId' = ?
                  AND metadata->>'active' = 'true'
                """, event.sourceType().name(), event.sourceId().toString());
        jdbc.update("""
                UPDATE rag.rag_index_chunks
                SET active = FALSE, invalidated_at = ?
                WHERE source_type = ? AND source_id = ? AND active = TRUE
                """, now(), event.sourceType().name(), event.sourceId());

        if (!event.deleted()) {
            for (int index = 0; index < vectorIds.size(); index++) {
                jdbc.update("""
                        INSERT INTO rag.rag_index_chunks
                            (vector_id, source_type, source_id, source_version, chunk_index,
                             active, created_at, invalidated_at)
                        VALUES (?, ?, ?, ?, ?, TRUE, ?, NULL)
                        ON CONFLICT (vector_id) DO UPDATE
                        SET active = TRUE, invalidated_at = NULL
                        """, vectorIds.get(index), event.sourceType().name(), event.sourceId(),
                        event.sourceVersion(), index, now());
            }
            activate(vectorIds);
        }

        jdbc.update("""
                INSERT INTO rag.rag_source_heads
                    (source_type, source_id, workspace_id, project_id, source_version, deleted, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_type, source_id) DO UPDATE
                SET workspace_id = EXCLUDED.workspace_id,
                    project_id = EXCLUDED.project_id,
                    source_version = EXCLUDED.source_version,
                    deleted = EXCLUDED.deleted,
                    updated_at = EXCLUDED.updated_at
                WHERE rag.rag_source_heads.source_version < EXCLUDED.source_version
                """, event.sourceType().name(), event.sourceId(), event.workspaceId(), event.projectId(),
                event.sourceVersion(), event.deleted(), now());
        Integer activeVersions = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT source_version)
                FROM rag.rag_index_chunks
                WHERE source_type = ? AND source_id = ? AND active = TRUE
                """, Integer.class, event.sourceType().name(), event.sourceId());
        if (activeVersions != null && activeVersions > 1) {
            throw new IllegalStateException("More than one active RAG source version");
        }
        completeJob(event.eventId());
        return FinalizeResult.ACTIVATED;
    }

    private void activate(List<UUID> vectorIds) {
        if (vectorIds.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(vectorIds.size(), "?"));
        jdbc.update("UPDATE rag.vector_store SET metadata = "
                        + "jsonb_set(metadata::jsonb, '{active}', 'true'::jsonb)::json WHERE id IN ("
                        + placeholders + ")",
                vectorIds.toArray());
    }

    @Override
    public void markFailed(UUID eventId, Throwable failure) {
        String message = failure == null || failure.getMessage() == null
                ? "indexing failure" : failure.getMessage();
        String safe = message.substring(0, Math.min(500, message.length()));
        jdbc.update("""
                UPDATE rag.rag_index_jobs
                SET status = CASE WHEN retry_count + 1 >= ? THEN 'DEAD' ELSE 'FAILED' END,
                    retry_count = retry_count + 1,
                    lease_until = NULL, last_error = ?, updated_at = ?
                WHERE event_id = ? AND status = 'PROCESSING'
                """, properties.getIndexMaxAttempts(), safe, now(), eventId);
    }

    private Job job(UUID eventId) {
        return jdbc.query("""
                SELECT status, retry_count, lease_until
                FROM rag.rag_index_jobs WHERE event_id = ? FOR UPDATE
                """, JdbcRagIndexStore::job, eventId).stream().findFirst().orElse(null);
    }

    private void completeJob(UUID eventId) {
        jdbc.update("""
                UPDATE rag.rag_index_jobs
                SET status = 'COMPLETED', completed_at = ?, lease_until = NULL,
                    last_error = NULL, updated_at = ?
                WHERE event_id = ?
                """, now(), now(), eventId);
    }

    private void exhaustJob(UUID eventId) {
        jdbc.update("""
                UPDATE rag.rag_index_jobs
                SET status = 'DEAD', lease_until = NULL, last_error = 'max attempts exhausted',
                    updated_at = ?
                WHERE event_id = ?
                """, now(), eventId);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void advisoryLock(RagSourceType sourceType, UUID sourceId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class,
                sourceType.name() + ":" + sourceId);
    }

    private static Job job(ResultSet result, int row) throws SQLException {
        return new Job(result.getString("status"), result.getInt("retry_count"),
                result.getObject("lease_until", OffsetDateTime.class));
    }

    private static Head head(ResultSet result, int row) throws SQLException {
        return new Head(result.getLong("source_version"),
                result.getObject("workspace_id", UUID.class), result.getObject("project_id", UUID.class));
    }

    private record Job(String status, int retryCount, OffsetDateTime leaseUntil) {}
    private record Head(long sourceVersion, UUID workspaceId, UUID projectId) {}
}
