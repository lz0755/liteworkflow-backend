package com.liteworkflow.ai.application;

import com.liteworkflow.ai.config.AiProperties;
import com.liteworkflow.common.core.error.BizException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiQuotaService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AiProperties properties;
    private final Clock clock;

    public AiQuotaService(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            AiProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    public Reservation reserve(UUID userId, long tokenBudget) {
        QuotaKey key = new QuotaKey(LocalDate.now(clock), userId);
        return transactions.execute(status -> reserveAtomically(key, Math.max(1, tokenBudget)));
    }

    public void settle(Reservation reservation, long actualTokens) {
        if (reservation == null) {
            return;
        }
        transactions.executeWithoutResult(status -> jdbc.update("""
                    UPDATE ai.ai_daily_quotas
                    SET reserved_tokens = CASE
                            WHEN reserved_tokens >= ? THEN reserved_tokens - ? ELSE 0 END,
                        token_count = token_count + ?
                    WHERE usage_date = ? AND user_id = ?
                    """, reservation.tokenBudget(), reservation.tokenBudget(), Math.max(0, actualTokens),
                    reservation.key().date(), reservation.key().userId()));
    }

    private Reservation reserveAtomically(QuotaKey key, long tokenBudget) {
        insertDailyRow(key);
        int updated = jdbc.update("""
                UPDATE ai.ai_daily_quotas
                SET request_count = request_count + 1,
                    reserved_tokens = reserved_tokens + ?
                WHERE usage_date = ? AND user_id = ?
                  AND request_count < ?
                  AND token_count + reserved_tokens + ? <= ?
                """, tokenBudget, key.date(), key.userId(), properties.getDailyRequestLimit(),
                tokenBudget, properties.getDailyTokenLimit());
        if (updated == 1) {
            return new Reservation(key, tokenBudget);
        }
        QuotaSnapshot snapshot = jdbc.queryForObject("""
                SELECT request_count, token_count, reserved_tokens
                FROM ai.ai_daily_quotas
                WHERE usage_date = ? AND user_id = ?
                """, (result, row) -> new QuotaSnapshot(
                        result.getInt("request_count"),
                        result.getLong("token_count"),
                        result.getLong("reserved_tokens")), key.date(), key.userId());
        if (snapshot.requestCount() >= properties.getDailyRequestLimit()) {
            throw new BizException(AiErrorCode.DAILY_REQUEST_LIMIT);
        }
        throw new BizException(AiErrorCode.DAILY_TOKEN_LIMIT);
    }

    private void insertDailyRow(QuotaKey key) {
        if (isPostgres()) {
            jdbc.update("""
                    INSERT INTO ai.ai_daily_quotas
                        (usage_date, user_id, request_count, token_count, reserved_tokens)
                    VALUES (?, ?, 0, 0, 0)
                    ON CONFLICT (usage_date, user_id) DO NOTHING
                    """, key.date(), key.userId());
            return;
        }
        // H2 is used only by fast tests and has no ON CONFLICT syntax. Unlike PostgreSQL,
        // a duplicate-key statement does not abort its transaction.
        try {
            jdbc.update("""
                    INSERT INTO ai.ai_daily_quotas
                        (usage_date, user_id, request_count, token_count, reserved_tokens)
                    VALUES (?, ?, 0, 0, 0)
                    """, key.date(), key.userId());
        } catch (DuplicateKeyException ignored) {
            // The atomic conditional UPDATE below remains authoritative.
        }
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
    }

    public record Reservation(QuotaKey key, long tokenBudget) {}
    public record QuotaKey(LocalDate date, UUID userId) {}
    private record QuotaSnapshot(int requestCount, long tokenCount, long reservedTokens) {}
}
