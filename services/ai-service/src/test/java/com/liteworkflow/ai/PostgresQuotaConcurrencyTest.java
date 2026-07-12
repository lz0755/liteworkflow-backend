package com.liteworkflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.ai.application.AiErrorCode;
import com.liteworkflow.ai.application.AiQuotaService;
import com.liteworkflow.ai.config.AiProperties;
import com.liteworkflow.common.core.error.BizException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresQuotaConcurrencyTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource firstDataSource;
    private static DataSource secondDataSource;

    @BeforeAll
    static void migrate() {
        firstDataSource = dataSource();
        secondDataSource = dataSource();
        Flyway.configure()
                .dataSource(firstDataSource)
                .schemas("ai")
                .defaultSchema("ai")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void clearQuotas() {
        new JdbcTemplate(firstDataSource).update("DELETE FROM ai.ai_daily_quotas");
    }

    @Test
    void concurrentFirstRequestsAcrossInstancesNeverOverReserve() throws Exception {
        AiProperties properties = quotaProperties(7, 10_000);
        AiQuotaService first = service(firstDataSource, properties);
        AiQuotaService second = service(secondDataSource, properties);
        UUID userId = UUID.randomUUID();
        int attempts = 24;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(attempts);
        List<Future<AiErrorCode>> results = new ArrayList<>();
        try {
            for (int index = 0; index < attempts; index++) {
                AiQuotaService selected = index % 2 == 0 ? first : second;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        selected.reserve(userId, 100);
                        return null;
                    } catch (BizException exception) {
                        return (AiErrorCode) exception.errorCode();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<AiErrorCode> errors = new ArrayList<>();
            int accepted = 0;
            for (Future<AiErrorCode> result : results) {
                AiErrorCode error = result.get(20, TimeUnit.SECONDS);
                if (error == null) {
                    accepted++;
                } else {
                    errors.add(error);
                }
            }
            assertThat(accepted).isEqualTo(7);
            assertThat(errors).hasSize(attempts - 7).containsOnly(AiErrorCode.DAILY_REQUEST_LIMIT);
            JdbcTemplate jdbc = new JdbcTemplate(firstDataSource);
            assertThat(jdbc.queryForObject("""
                    SELECT request_count FROM ai.ai_daily_quotas
                    WHERE usage_date = ? AND user_id = ?
                    """, Integer.class, LocalDate.now(Clock.systemUTC()), userId)).isEqualTo(7);
            assertThat(jdbc.queryForObject("""
                    SELECT reserved_tokens FROM ai.ai_daily_quotas
                    WHERE usage_date = ? AND user_id = ?
                    """, Long.class, LocalDate.now(Clock.systemUTC()), userId)).isEqualTo(700L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void tokenReservationHonorsExactBoundaryAcrossInstances() {
        AiProperties properties = quotaProperties(100, 100);
        AiQuotaService first = service(firstDataSource, properties);
        AiQuotaService second = service(secondDataSource, properties);
        UUID userId = UUID.randomUUID();

        AiQuotaService.Reservation sixty = first.reserve(userId, 60);
        assertThatThrownByCode(() -> second.reserve(userId, 41), AiErrorCode.DAILY_TOKEN_LIMIT);
        first.settle(sixty, 60);
        AiQuotaService.Reservation forty = second.reserve(userId, 40);
        second.settle(forty, 40);

        JdbcTemplate jdbc = new JdbcTemplate(firstDataSource);
        assertThat(jdbc.queryForObject("""
                SELECT token_count FROM ai.ai_daily_quotas
                WHERE usage_date = ? AND user_id = ?
                """, Long.class, LocalDate.now(Clock.systemUTC()), userId)).isEqualTo(100L);
        assertThatThrownByCode(() -> first.reserve(userId, 1), AiErrorCode.DAILY_TOKEN_LIMIT);
    }

    private static void assertThatThrownByCode(Runnable action, AiErrorCode expected) {
        try {
            action.run();
            throw new AssertionError("Expected quota rejection " + expected);
        } catch (BizException exception) {
            assertThat(exception.errorCode()).isEqualTo(expected);
        }
    }

    private static AiQuotaService service(DataSource dataSource, AiProperties properties) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new AiQuotaService(
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                properties,
                Clock.systemUTC());
    }

    private static AiProperties quotaProperties(int requestLimit, long tokenLimit) {
        AiProperties properties = new AiProperties();
        properties.setDailyRequestLimit(requestLimit);
        properties.setDailyTokenLimit(tokenLimit);
        return properties;
    }

    private static DataSource dataSource() {
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
