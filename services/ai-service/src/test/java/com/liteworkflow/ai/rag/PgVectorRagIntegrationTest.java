package com.liteworkflow.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.ai.config.RagProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PgVectorRagIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg16-trixie")
                    .asCompatibleSubstituteFor("postgres"));

    private static DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcRagIndexStore indexStore;
    private RagIndexService indexService;
    private RagRetrievalService retrieval;
    private PgVectorStore vectorStore;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS ai");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS rag");
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("ai")
                .defaultSchema("ai")
                .locations("classpath:db/ai-migration", "classpath:db/rag-migration")
                .placeholders(Map.of("embeddingDimensions", "3"))
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM rag.rag_index_chunks");
        jdbc.update("DELETE FROM rag.rag_index_jobs");
        jdbc.update("DELETE FROM rag.rag_source_heads");
        jdbc.update("DELETE FROM rag.vector_store");
        RagProperties properties = new RagProperties();
        properties.setEmbeddingModel("fixed-three-dimensional-test-model");
        properties.setEmbeddingDimensions(3);
        properties.setTopK(10);
        properties.setSimilarityThreshold(0.0);
        FixedEmbeddingModel embeddingModel = new FixedEmbeddingModel();
        vectorStore = PgVectorStore.builder(jdbc, embeddingModel)
                .schemaName("rag")
                .vectorTableName("vector_store")
                .dimensions(3)
                .indexType(PgVectorStore.PgIndexType.NONE)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(true)
                .build();
        vectorStore.afterPropertiesSet();
        var transactionManager = new DataSourceTransactionManager(dataSource);
        indexStore = new JdbcRagIndexStore(
                jdbc, new TransactionTemplate(transactionManager), Clock.systemUTC(), properties);
        indexService = new RagIndexService(vectorStore, indexStore);
        retrieval = new RagRetrievalService(vectorStore, properties);
    }

    @Test
    void duplicateOutOfOrderUpdateAndCrossProjectIsolationHoldInPostgres() {
        UUID workspace = UUID.randomUUID();
        UUID projectA = UUID.randomUUID();
        UUID projectB = UUID.randomUUID();
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        RagSourceEvent first = event(workspace, projectA, sourceA, 1,
                UUID.randomUUID(), List.of("old part one", "old part two"));

        assertThat(indexService.index(first)).isEqualTo(RagIndexService.Result.ACTIVATED);
        assertThat(indexService.index(first)).isEqualTo(RagIndexService.Result.DUPLICATE);
        assertThat(indexService.index(event(workspace, projectA, sourceA, 3,
                UUID.randomUUID(), List.of("current project A document"))))
                .isEqualTo(RagIndexService.Result.ACTIVATED);
        assertThat(indexService.index(event(workspace, projectA, sourceA, 2,
                UUID.randomUUID(), List.of("late stale document"))))
                .isEqualTo(RagIndexService.Result.STALE);
        assertThat(indexService.index(event(workspace, projectB, sourceB, 1,
                UUID.randomUUID(), List.of("private project B document"))))
                .isEqualTo(RagIndexService.Result.ACTIVATED);

        RagRetrievalService.Retrieval result = retrieval.retrieve(workspace, projectA, "document");

        assertThat(result.documents()).hasSize(1);
        assertThat(result.documents().getFirst().getText()).isEqualTo("current project A document");
        assertThat(result.sources()).allSatisfy(source -> {
            assertThat(source.sourceId()).isEqualTo(sourceA);
            assertThat(source.sourceVersion()).isEqualTo(3);
        });
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM rag.vector_store
                WHERE metadata->>'sourceId' = ? AND metadata->>'active' = 'true'
                """, Integer.class, sourceA.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag.vector_store", Integer.class)).isEqualTo(4);
    }

    @Test
    void documentVersionOneToTwoToDeleteLeavesNoRetrievableOrActiveOldVersion() {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        assertThat(indexService.index(event(workspace, project, document, 1,
                UUID.randomUUID(), List.of("version one"))))
                .isEqualTo(RagIndexService.Result.ACTIVATED);
        assertThat(indexService.index(event(workspace, project, document, 2,
                UUID.randomUUID(), List.of("version two"))))
                .isEqualTo(RagIndexService.Result.ACTIVATED);
        RagSourceEvent deleted = new RagSourceEvent(UUID.randomUUID(), workspace, project,
                RagSourceType.DOCUMENT, document, 3, "Project document", List.of(), true);
        assertThat(indexService.index(deleted)).isEqualTo(RagIndexService.Result.ACTIVATED);

        assertThat(retrieval.retrieve(workspace, project, "version").documents()).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM rag.rag_index_chunks
                WHERE source_type = 'DOCUMENT' AND source_id = ? AND active = TRUE
                """, Integer.class, document)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT deleted FROM rag.rag_source_heads
                WHERE source_type = 'DOCUMENT' AND source_id = ?
                """, Boolean.class, document)).isTrue();
    }

    @Test
    void expiredProcessingLeaseIsReclaimedAfterWorkerCrash() {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        RagSourceEvent event = event(workspace, project, document, 1,
                UUID.randomUUID(), List.of("recovered content"));
        assertThat(indexStore.claim(event, false)).isEqualTo(RagIndexStore.Claim.PROCESS);
        jdbc.update("""
                UPDATE rag.rag_index_jobs
                SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE event_id = ?
                """, event.eventId());

        assertThat(indexService.index(event)).isEqualTo(RagIndexService.Result.ACTIVATED);
        assertThat(jdbc.queryForObject("""
                SELECT retry_count FROM rag.rag_index_jobs WHERE event_id = ?
                """, Integer.class, event.eventId())).isEqualTo(1);
        assertThat(retrieval.retrieve(workspace, project, "recovered").documents()).hasSize(1);
    }

    @Test
    void brokerRedeliveryImmediatelyTakesOverFutureProcessingLease() {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        RagSourceEvent event = event(workspace, project, document, 1,
                UUID.randomUUID(), List.of("immediate redelivery"));
        assertThat(indexStore.claim(event, false)).isEqualTo(RagIndexStore.Claim.PROCESS);
        assertThat(jdbc.queryForObject("""
                SELECT lease_until > CURRENT_TIMESTAMP FROM rag.rag_index_jobs WHERE event_id = ?
                """, Boolean.class, event.eventId())).isTrue();

        assertThat(indexService.index(event, true)).isEqualTo(RagIndexService.Result.ACTIVATED);
        assertThat(jdbc.queryForObject("""
                SELECT retry_count FROM rag.rag_index_jobs WHERE event_id = ?
                """, Integer.class, event.eventId())).isEqualTo(1);
        assertThat(retrieval.retrieve(workspace, project, "redelivery").documents()).hasSize(1);
    }

    @Test
    void repeatedCrashRedeliveriesEndInDeadState() {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        RagSourceEvent event = event(workspace, project, UUID.randomUUID(), 1,
                UUID.randomUUID(), List.of("never completed"));
        assertThat(indexStore.claim(event, false)).isEqualTo(RagIndexStore.Claim.PROCESS);
        assertThat(indexStore.claim(event, true)).isEqualTo(RagIndexStore.Claim.PROCESS);
        assertThat(indexStore.claim(event, true)).isEqualTo(RagIndexStore.Claim.PROCESS);
        assertThat(indexStore.claim(event, true)).isEqualTo(RagIndexStore.Claim.EXHAUSTED);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM rag.rag_index_jobs WHERE event_id = ?
                """, String.class, event.eventId())).isEqualTo("DEAD");
    }

    @Test
    void finalOrdinaryFailureAtomicallyRecordsDeadState() {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        RagSourceEvent event = event(workspace, project, UUID.randomUUID(), 1,
                UUID.randomUUID(), List.of("provider keeps failing"));
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(indexStore.claim(event, false)).isEqualTo(RagIndexStore.Claim.PROCESS);
            indexStore.markFailed(event.eventId(), new IllegalStateException("provider unavailable"));
        }

        assertThat(jdbc.queryForObject("""
                SELECT status FROM rag.rag_index_jobs WHERE event_id = ?
                """, String.class, event.eventId())).isEqualTo("DEAD");
        assertThat(indexStore.claim(event, true)).isEqualTo(RagIndexStore.Claim.EXHAUSTED);
    }

    @Test
    void concurrentFirstWritesFromDifferentConnectionsCannotLetLowVersionWin() throws Exception {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        RagSourceEvent low = event(workspace, project, document, 2,
                UUID.randomUUID(), List.of("low version"));
        RagSourceEvent high = event(workspace, project, document, 3,
                UUID.randomUUID(), List.of("high version"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var lowFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return indexService.index(low);
            });
            var highFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return indexService.index(high);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            lowFuture.get(20, TimeUnit.SECONDS);
            highFuture.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("""
                SELECT source_version FROM rag.rag_source_heads
                WHERE source_type = 'DOCUMENT' AND source_id = ?
                """, Long.class, document)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT source_version) FROM rag.rag_index_chunks
                WHERE source_type = 'DOCUMENT' AND source_id = ? AND active = TRUE
                """, Integer.class, document)).isEqualTo(1);
        assertThat(retrieval.retrieve(workspace, project, "version").documents())
                .singleElement().satisfies(value -> assertThat(value.getText()).isEqualTo("high version"));
    }

    private static RagSourceEvent event(
            UUID workspace, UUID project, UUID source, long version, UUID eventId, List<String> chunks) {
        return new RagSourceEvent(eventId, workspace, project, RagSourceType.DOCUMENT,
                source, version, "Project document", chunks, false);
    }

    @Test
    void retryAfterPartialWriteUpsertsSameVectorIdsWithoutDuplicatingActiveRecords() {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        RagSourceEvent event = event(workspace, project, document, 1,
                UUID.randomUUID(), List.of("recoverable content"));

        // A worker claimed the event, embedded and wrote the chunk (active=false),
        // then crashed before finalizeVersion could activate it. markFailed leaves
        // the job retryable with a dormant vector behind.
        assertThat(indexStore.claim(event, false)).isEqualTo(RagIndexStore.Claim.PROCESS);
        vectorStore.add(List.of(dormantDocument(event, 0, "recoverable content")));
        indexStore.markFailed(event.eventId(), new IllegalStateException("crash before finalize"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag.vector_store WHERE metadata->>'sourceId' = ?",
                Integer.class, document.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM rag.vector_store
                WHERE metadata->>'sourceId' = ? AND metadata->>'active' = 'true'
                """, Integer.class, document.toString())).isZero();

        // Broker redelivery re-runs embed + add (upsert on the identical deterministic id) + finalize.
        assertThat(indexService.index(event, true)).isEqualTo(RagIndexService.Result.ACTIVATED);

        // No duplicate vector row, exactly one active record, and the content is retrievable.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag.vector_store WHERE metadata->>'sourceId' = ?",
                Integer.class, document.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM rag.vector_store
                WHERE metadata->>'sourceId' = ? AND metadata->>'active' = 'true'
                """, Integer.class, document.toString())).isEqualTo(1);
        assertThat(retrieval.retrieve(workspace, project, "recoverable").documents())
                .singleElement().satisfies(value -> assertThat(value.getText()).isEqualTo("recoverable content"));
    }

    // Mirrors RagIndexService.vectorIds/documents so the test can pre-stage a dormant
    // partial write with the same deterministic id the retry will upsert.
    private static Document dormantDocument(RagSourceEvent event, int chunkIndex, String text) {
        String key = event.sourceType() + ":" + event.sourceId() + ":"
                + event.sourceVersion() + ":" + chunkIndex;
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(Map.of(
                        "workspaceId", event.workspaceId().toString(),
                        "projectId", event.projectId().toString(),
                        "sourceType", event.sourceType().name(),
                        "sourceId", event.sourceId().toString(),
                        "sourceVersion", event.sourceVersion(),
                        "chunkIndex", chunkIndex,
                        "title", event.title(),
                        "active", false))
                .build();
    }


    private static final class FixedEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = java.util.stream.IntStream
                    .range(0, request.getInstructions().size())
                    .mapToObj(index -> new Embedding(new float[] {1, 0, 0}, index))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return new float[] {1, 0, 0};
        }

        @Override
        public int dimensions() {
            return 3;
        }
    }
}
