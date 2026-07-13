package com.liteworkflow.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.core.application.IssueApplicationService;
import com.liteworkflow.core.application.ProjectApplicationService;
import com.liteworkflow.core.application.WorkspaceApplicationService;
import com.liteworkflow.core.application.WorkspaceMemberApplicationService;
import com.liteworkflow.core.application.WorkspacePermissionCache;
import com.liteworkflow.core.directory.CoreAmqpConfiguration;
import com.liteworkflow.core.directory.IdentityUserEventPayload;
import com.liteworkflow.core.directory.UserDirectoryProjectionService;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.LocalOutboxEvent;
import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.OutboxStatus;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.request.CreateIssueRequest;
import com.liteworkflow.core.dto.request.CreateProjectRequest;
import com.liteworkflow.core.dto.request.CreateWorkspaceRequest;
import com.liteworkflow.core.dto.response.IssueResponse;
import com.liteworkflow.core.outbox.OutboxDispatchService;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.LocalOutboxEventRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "debug=false",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=core",
        "spring.flyway.default-schema=core",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.retry.enabled=true",
        "spring.rabbitmq.listener.simple.retry.max-attempts=2",
        "spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=10ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=2",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true",
        "spring.rabbitmq.template.mandatory=true",
        "spring.task.scheduling.enabled=false",
        "liteworkflow.core.permission-cache-enabled=true",
        "liteworkflow.core.outbox.immediate-publish=false",
        "liteworkflow.core.outbox.scheduling-enabled=false",
        "liteworkflow.core.outbox.max-retries=5",
        "liteworkflow.core.outbox.retry-delay=20ms",
        "liteworkflow.core.outbox.publisher-confirm-timeout=2s",
        "management.health.rabbit.enabled=false",
        "management.health.redis.enabled=false",
        "logging.level.org.hibernate.SQL=WARN"
})
class InfrastructureTestcontainersTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg16-trixie")
                    .asCompatibleSubstituteFor("postgres"));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.2.7-alpine3.22"))
            .withExposedPorts(6379);

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1.8-management-alpine"));

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkspacePermissionCache workspacePermissionCache;
    @Autowired private UserDirectoryProjectionService projectionService;
    @Autowired private UserDirectoryRepository userDirectoryRepository;
    @Autowired private ConsumedEventRepository consumedEventRepository;
    @Autowired private LocalOutboxEventRepository outboxRepository;
    @Autowired private OutboxDispatchService outboxDispatchService;
    @Autowired private WorkspaceApplicationService workspaceService;
    @Autowired private WorkspaceMemberApplicationService workspaceMemberService;
    @Autowired private ProjectApplicationService projectService;
    @Autowired private IssueApplicationService issueService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private IssueRepository issueRepository;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.rabbitmq.host", RABBIT::getHost);
        properties.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        properties.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        properties.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @BeforeEach
    void resetInfrastructure() {
        rabbitAdmin.purgeQueue(CoreAmqpConfiguration.IDENTITY_USER_QUEUE, false);
        rabbitAdmin.purgeQueue(CoreAmqpConfiguration.IDENTITY_USER_DLQ, false);
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        jdbc.execute("TRUNCATE TABLE core.user_directory, core.consumed_events, "
                + "core.local_outbox_events CASCADE");
    }

    @Test
    void emptyPostgresMigrationRedisCacheAndRabbitTopologyAreOperational() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM core.flyway_schema_history "
                        + "WHERE success AND version IS NOT NULL", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT current_database()", String.class)).isEqualTo(POSTGRES.getDatabaseName());

        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        workspacePermissionCache.put(workspaceId, userId, WorkspaceRole.ADMIN);
        assertThat(workspacePermissionCache.get(workspaceId, userId)).contains(WorkspaceRole.ADMIN);
        workspacePermissionCache.evict(workspaceId, userId);
        assertThat(workspacePermissionCache.get(workspaceId, userId)).isEmpty();

        assertThat(rabbitAdmin.getQueueInfo(CoreAmqpConfiguration.IDENTITY_USER_QUEUE)).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo(CoreAmqpConfiguration.IDENTITY_USER_DLQ)).isNotNull();
    }

    @Test
    void rabbitConsumerIgnoresDuplicateAndOutOfOrderSourceVersions() {
        UUID userId = UUID.randomUUID();
        EventEnvelope<IdentityUserEventPayload> newest = identityEvent(
                UUID.randomUUID(), 1, userId, "new@example.com", "New Name", 3);
        rabbit.convertAndSend(CoreAmqpConfiguration.IDENTITY_EXCHANGE, newest.eventType(), newest);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(userDirectoryRepository.findById(userId)).get()
                        .extracting(value -> value.getSourceVersion()).isEqualTo(3L));

        rabbit.convertAndSend(CoreAmqpConfiguration.IDENTITY_EXCHANGE, newest.eventType(), newest);
        rabbit.convertAndSend(CoreAmqpConfiguration.IDENTITY_EXCHANGE, "identity.user.updated",
                identityEvent(UUID.randomUUID(), 1, userId, "old@example.com", "Old Name", 2));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(consumedEventRepository.count()).isEqualTo(2));
        assertThat(userDirectoryRepository.findById(userId)).get().satisfies(projected -> {
            assertThat(projected.getNormalizedEmail()).isEqualTo("new@example.com");
            assertThat(projected.getDisplayName()).isEqualTo("New Name");
            assertThat(projected.getSourceVersion()).isEqualTo(3);
        });
        assertThat(rabbit.receive(CoreAmqpConfiguration.IDENTITY_USER_DLQ)).isNull();
    }

    @Test
    void unsupportedIdentityContractVersionIsRejectedToDlq() {
        UUID userId = UUID.randomUUID();
        EventEnvelope<IdentityUserEventPayload> unsupported = identityEvent(
                UUID.randomUUID(), 2, userId, "future@example.com", "Future Contract", 1);

        rabbit.convertAndSend(CoreAmqpConfiguration.IDENTITY_EXCHANGE, unsupported.eventType(), unsupported);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(rabbitAdmin.getQueueInfo(CoreAmqpConfiguration.IDENTITY_USER_DLQ).getMessageCount())
                        .isPositive());
        Message deadLetter = rabbit.receive(CoreAmqpConfiguration.IDENTITY_USER_DLQ);
        assertThat(deadLetter).isNotNull();
        String exceptionMessage = deadLetter.getMessageProperties()
                .getHeader(RepublishMessageRecoverer.X_EXCEPTION_MESSAGE);
        String originalRoutingKey = deadLetter.getMessageProperties()
                .getHeader(RepublishMessageRecoverer.X_ORIGINAL_ROUTING_KEY);
        assertThat(exceptionMessage)
                .contains("Unsupported identity user event version 2");
        assertThat(originalRoutingKey).isEqualTo(unsupported.eventType());
        assertThat(userDirectoryRepository.findById(userId)).isEmpty();
    }

    @Test
    void failedOutboxDeliveryRecoversThroughRealRabbitPublisherConfirms() {
        String suffix = UUID.randomUUID().toString();
        String exchangeName = "m13.recovery." + suffix;
        String queueName = "m13.recovery." + suffix;
        String routingKey = "m13.outbox.recovered";
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        EventEnvelope<Map<String, String>> envelope = new EventEnvelope<>(
                eventId, routingKey, 1, Instant.now(), new EventScope(null, null, aggregateId),
                aggregateId, Map.of("state", "durable"), Map.of());
        outboxRepository.saveAndFlush(new LocalOutboxEvent(
                eventId, routingKey, exchangeName, routingKey, "M13_TEST", aggregateId,
                null, null, aggregateId, objectMapper.valueToTree(envelope), Instant.now()));

        outboxDispatchService.dispatch(eventId);
        assertThat(outboxRepository.findById(eventId)).get()
                .extracting(LocalOutboxEvent::getStatus).isEqualTo(OutboxStatus.FAILED);

        TopicExchange exchange = new TopicExchange(exchangeName, false, true);
        Queue queue = new Queue(queueName, false, true, true);
        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(routingKey));
        try {
            await().pollInterval(Duration.ofMillis(20)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                outboxDispatchService.recoverPending();
                assertThat(outboxRepository.findById(eventId)).get()
                        .extracting(LocalOutboxEvent::getStatus).isEqualTo(OutboxStatus.PUBLISHED);
            });
            Message delivered = rabbit.receive(queueName, 2_000);
            assertThat(delivered).isNotNull();
            assertThat(new String(delivered.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains(eventId.toString(), "\"state\":\"durable\"");
        } finally {
            rabbitAdmin.deleteQueue(queueName);
            rabbitAdmin.deleteExchange(exchangeName);
        }
    }

    @Test
    void postgresLockPreservesOneWorkspaceOwnerUnderConcurrentDemotion() throws Exception {
        UUID firstOwner = activeUser("first-owner@example.com", "First Owner");
        UUID secondOwner = activeUser("second-owner@example.com", "Second Owner");
        UUID workspaceId = workspaceService.create(
                firstOwner, new CreateWorkspaceRequest("Concurrent Owners", null)).id();
        workspaceMemberService.add(firstOwner, workspaceId, secondOwner, WorkspaceRole.OWNER);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> results = List.of(
                    executor.submit(() -> demoteOwner(ready, start, firstOwner, workspaceId)),
                    executor.submit(() -> demoteOwner(ready, start, secondOwner, workspaceId)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> result : results) outcomes.add(result.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
            assertThat(outcomes).filteredOn(java.util.Objects::nonNull).singleElement()
                    .isInstanceOf(BizException.class);
            assertThat(workspaceMemberRepository.countByWorkspaceIdAndStatusAndRole(
                    workspaceId, MemberStatus.ACTIVE, WorkspaceRole.OWNER)).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void postgresAllocatesUniqueIssueNumbersUnderConcurrency() throws Exception {
        UUID ownerId = activeUser("issue-owner@example.com", "Issue Owner");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Concurrent Issues", null)).id();
        UUID projectId = projectService.create(
                ownerId, workspaceId, new CreateProjectRequest("Postgres Project", null)).id();
        int attempts = 12;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<IssueResponse>> futures = new ArrayList<>();
            for (int index = 0; index < attempts; index++) {
                int issueIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return issueService.create(ownerId, projectId, new CreateIssueRequest(
                            "Issue " + issueIndex, null, null, Set.of(), Set.of(), null));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<Long> numbers = new HashSet<>();
            for (Future<IssueResponse> future : futures) {
                numbers.add(future.get(20, TimeUnit.SECONDS).issueNumber());
            }
            assertThat(numbers).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, attempts).boxed().toList());
            assertThat(issueRepository.count()).isEqualTo(attempts);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable demoteOwner(
            CountDownLatch ready, CountDownLatch start, UUID actorId, UUID workspaceId) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            workspaceMemberService.changeRole(actorId, workspaceId, actorId, WorkspaceRole.MEMBER);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private UUID activeUser(String email, String displayName) {
        UUID userId = UUID.randomUUID();
        projectionService.consume(identityEvent(
                UUID.randomUUID(), 1, userId, email, displayName, 1));
        return userId;
    }

    private static EventEnvelope<IdentityUserEventPayload> identityEvent(
            UUID eventId, int contractVersion, UUID userId,
            String email, String displayName, long sourceVersion) {
        return new EventEnvelope<>(
                eventId,
                sourceVersion == 1 ? "identity.user.registered" : "identity.user.updated",
                contractVersion,
                Instant.now(),
                new EventScope(null, null, userId),
                userId,
                new IdentityUserEventPayload(
                        userId, email, displayName, AccountStatus.ACTIVE, sourceVersion),
                Map.of());
    }
}
