package com.liteworkflow.infra.notification;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Claims an event with one atomic database statement inside the projection transaction. */
@Repository
public class ConsumedEventClaimRepository {

    private static final String POSTGRES_CLAIM_SQL = """
            INSERT INTO infra.consumed_events (event_id, event_type, consumed_at)
            VALUES (?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private static final String H2_CLAIM_SQL = """
            MERGE INTO infra.consumed_events AS target
            USING (VALUES (
                CAST(? AS UUID),
                CAST(? AS VARCHAR(128)),
                CAST(? AS TIMESTAMP WITH TIME ZONE)
            )) AS source (event_id, event_type, consumed_at)
            ON target.event_id = source.event_id
            WHEN NOT MATCHED THEN
                INSERT (event_id, event_type, consumed_at)
                VALUES (source.event_id, source.event_type, source.consumed_at)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final boolean h2;

    public ConsumedEventClaimRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.h2 = isH2(dataSource);
    }

    public boolean claim(UUID eventId, String eventType, Instant consumedAt) {
        try {
            return jdbcTemplate.update(
                            h2 ? H2_CLAIM_SQL : POSTGRES_CLAIM_SQL,
                            eventId,
                            eventType,
                            Timestamp.from(consumedAt))
                    == 1;
        } catch (DuplicateKeyException exception) {
            // H2's MERGE can race after both transactions observe no row. PostgreSQL uses
            // ON CONFLICT and never takes this path; H2 keeps its transaction usable here.
            if (h2) {
                return false;
            }
            throw exception;
        }
    }

    private boolean isH2(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot determine consumed-events database dialect", exception);
        }
    }
}
