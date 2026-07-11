package com.liteworkflow.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M4RealInfrastructureSmokeTest {

    private static final String JWT_SECRET =
            "VGhpcy1pcy1hLXJlYWwtaW5mcmFzdHJ1Y3R1cmUtc21va2UtdGVzdC1rZXkh";
    private static final String JWT_ISSUER = "liteworkflow-m4-smoke";
    private static final String RABBIT_USER = "smoke";
    private static final String RABBIT_PASSWORD = "smoke-password";
    private static final String RABBIT_VHOST = "/";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);

    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(compatiblePostgresImage())
            .withDatabaseName("liteworkflow_smoke")
            .withUsername("liteworkflow")
            .withPassword("postgres-smoke-password");
    private final RabbitMQContainer rabbitmq = new RabbitMQContainer(image(
                    "liteworkflow.smoke.rabbitmq-image",
                    "rabbitmq:4.1.8-management-alpine"))
            .withAdminUser(RABBIT_USER)
            .withAdminPassword(RABBIT_PASSWORD);
    private final GenericContainer<?> redis = new GenericContainer<>(image(
                    "liteworkflow.smoke.redis-image",
                    "redis:8.2.7-alpine3.22"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private ConfigurableApplicationContext coreContext;
    private ConfigurableApplicationContext identityContext;
    private ConfigurableApplicationContext gatewayContext;
    private String gatewayBaseUrl;
    private String workEventProbeQueue;

    @BeforeAll
    void startRealInfrastructureAndServices() {
        try {
            Startables.deepStart(Stream.of(postgres, rabbitmq, redis)).join();
            coreContext = startCore();
            declareWorkEventProbe();
            identityContext = startIdentity();
            gatewayContext = startGateway(port(identityContext), port(coreContext));
            gatewayBaseUrl = "http://127.0.0.1:" + port(gatewayContext);
        } catch (RuntimeException | Error failure) {
            closeResources();
            throw failure;
        }
    }

    @AfterAll
    void stopServicesAndInfrastructure() {
        closeResources();
    }

    @Test
    void registrationDirectoryWorkspaceAndMemberLifecycleWorkThroughGateway() throws Exception {
        assertMigrationApplied("identity", "V1__identity_m3.sql");
        assertMigrationApplied("core", "V1__core_m4.sql");

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-" + suffix + "@example.test";
        String candidateEmail = "candidate-" + suffix + "@example.test";
        String password = "Correct horse battery staple";

        String ownerToken = register(ownerEmail, "Smoke Owner", password);
        String candidateToken = register(candidateEmail, "Smoke Candidate", password);
        UUID candidateId = UUID.fromString(success(
                        request("GET", "/api/v1/auth/me", candidateToken, null), 200)
                .path("data")
                .path("userId")
                .asText());

        awaitCoreProjection(ownerToken);
        awaitCoreProjection(candidateToken);

        JsonNode workspace = success(request(
                        "POST",
                        "/api/v1/workspaces",
                        ownerToken,
                        Map.of("name", "M4 Real Smoke Workspace", "description", "Testcontainers smoke")), 201)
                .path("data");
        UUID workspaceId = UUID.fromString(workspace.path("id").asText());
        assertThat(workspace.path("currentUserRole").asText()).isEqualTo("OWNER");

        JsonNode candidate = search(ownerToken, workspaceId, candidateEmail);
        assertThat(candidate.path("userId").asText()).isEqualTo(candidateId.toString());
        assertThat(candidate.path("eligible").asBoolean()).isTrue();

        JsonNode added = success(request(
                        "POST",
                        "/api/v1/workspaces/" + workspaceId + "/members",
                        ownerToken,
                        Map.of("userId", candidateId, "role", "MEMBER")), 201)
                .path("data");
        assertThat(added.path("role").asText()).isEqualTo("MEMBER");

        JsonNode changed = success(request(
                        "PATCH",
                        "/api/v1/workspaces/" + workspaceId + "/members/" + candidateId,
                        ownerToken,
                        Map.of("role", "VIEWER")), 200)
                .path("data");
        assertThat(changed.path("role").asText()).isEqualTo("VIEWER");

        JsonNode members = success(request(
                        "GET",
                        "/api/v1/workspaces/" + workspaceId + "/members",
                        ownerToken,
                        null), 200)
                .path("data")
                .path("records");
        assertThat(findRecord(members, candidateId).path("role").asText()).isEqualTo("VIEWER");

        success(request(
                "DELETE",
                "/api/v1/workspaces/" + workspaceId + "/members/" + candidateId,
                ownerToken,
                null), 200);

        HttpResult revoked = request(
                "GET", "/api/v1/workspaces/" + workspaceId, candidateToken, null);
        assertThat(revoked.status()).isEqualTo(403);
        assertThat(revoked.body().path("code").asText()).isEqualTo("CORE_WORKSPACE_403");

        JsonNode eligibleAgain = search(ownerToken, workspaceId, candidateEmail);
        assertThat(eligibleAgain.path("userId").asText()).isEqualTo(candidateId.toString());
        assertThat(eligibleAgain.path("eligible").asBoolean()).isTrue();

        assertThat(awaitWorkEventTypes())
                .containsExactlyInAnyOrder(
                        "workspace.member.added",
                        "workspace.member.added",
                        "workspace.member.role.changed",
                        "workspace.member.removed");
        assertCoreOutboxPublished(4);
    }

    private void declareWorkEventProbe() {
        workEventProbeQueue = "smoke.m4.work-events." + UUID.randomUUID();
        RabbitAdmin admin = coreContext.getBean(RabbitAdmin.class);
        admin.declareQueue(new Queue(workEventProbeQueue, false, false, false));
        admin.declareBinding(new Binding(
                workEventProbeQueue,
                Binding.DestinationType.QUEUE,
                "work.event.exchange",
                "workspace.member.#",
                null));
    }

    private List<String> awaitWorkEventTypes() throws Exception {
        RabbitTemplate template = coreContext.getBean(RabbitTemplate.class);
        List<String> eventTypes = new ArrayList<>();
        long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
        while (eventTypes.size() < 4 && System.nanoTime() < deadline) {
            Message message = template.receive(workEventProbeQueue, 1_000);
            if (message == null) {
                continue;
            }
            String headerType = message.getMessageProperties().getHeader("x-event-type");
            JsonNode envelope = objectMapper.readTree(message.getBody());
            assertThat(envelope.path("eventType").asText()).isEqualTo(headerType);
            eventTypes.add(headerType);
        }
        assertThat(eventTypes).as("work events received from RabbitMQ").hasSize(4);
        return eventTypes;
    }

    private void assertCoreOutboxPublished(int expectedCount) throws Exception {
        try (var connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "select count(*) from core.local_outbox_events where status = 'PUBLISHED'")) {
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(expectedCount);
            }
        }
    }

    private ConfigurableApplicationContext startCore() {
        Map<String, Object> properties = serviceProperties("core");
        properties.put("liteworkflow.core.permission-cache-enabled", "true");
        properties.put("liteworkflow.core.outbox.immediate-publish", "true");
        properties.put("liteworkflow.core.outbox.scheduling-enabled", "false");
        properties.put("spring.flyway.locations", migrationLocation("core-service"));
        properties.put("spring.autoconfigure.exclude", servletSecurityExclusions());
        return new SpringApplicationBuilder(applicationClass("com.liteworkflow.core.CoreServiceApplication"))
                .web(WebApplicationType.SERVLET)
                .run(commandLine(properties));
    }

    private ConfigurableApplicationContext startIdentity() {
        Map<String, Object> properties = serviceProperties("identity");
        properties.put("liteworkflow.identity.outbox.immediate-publish", "true");
        properties.put("liteworkflow.identity.outbox.scheduling-enabled", "false");
        properties.put("spring.flyway.locations", migrationLocation("identity-service"));
        properties.put("spring.autoconfigure.exclude", servletSecurityExclusions());
        return new SpringApplicationBuilder(applicationClass("com.liteworkflow.identity.IdentityServiceApplication"))
                .web(WebApplicationType.SERVLET)
                .run(commandLine(properties));
    }

    private ConfigurableApplicationContext startGateway(int identityPort, int corePort) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.application.name", "gateway-service-smoke");
        properties.put("spring.main.web-application-type", "reactive");
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", "0");
        properties.put("spring.main.banner-mode", "off");
        properties.put("debug", "false");
        properties.put("spring.data.redis.host", redis.getHost());
        properties.put("spring.data.redis.port", redis.getMappedPort(6379));
        properties.put("liteworkflow.security.jwt.secret", JWT_SECRET);
        properties.put("liteworkflow.security.jwt.issuer", JWT_ISSUER);
        properties.put("liteworkflow.gateway.services.identity", "http://127.0.0.1:" + identityPort);
        properties.put("liteworkflow.gateway.services.core", "http://127.0.0.1:" + corePort);
        properties.put("liteworkflow.gateway.services.infra", "http://127.0.0.1:1");
        properties.put("liteworkflow.gateway.services.ai", "http://127.0.0.1:1");
        properties.put("liteworkflow.gateway.openapi.enabled", "false");
        properties.put("springdoc.api-docs.enabled", "false");
        properties.put("springdoc.swagger-ui.enabled", "false");
        properties.put("management.health.redis.enabled", "false");
        properties.put("logging.level.root", "WARN");
        properties.put(
                "spring.autoconfigure.exclude",
                String.join(",",
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                        "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration",
                        "org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration"));
        return new SpringApplicationBuilder(applicationClass("com.liteworkflow.gateway.GatewayServiceApplication"))
                .web(WebApplicationType.REACTIVE)
                .run(commandLine(properties));
    }

    private Map<String, Object> serviceProperties(String schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.application.name", schema + "-service-smoke");
        properties.put("spring.main.web-application-type", "servlet");
        properties.put("spring.cloud.gateway.server.webflux.enabled", "false");
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", "0");
        properties.put("spring.main.banner-mode", "off");
        properties.put("debug", "false");
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());
        properties.put("spring.jpa.hibernate.ddl-auto", "none");
        properties.put("spring.jpa.open-in-view", "false");
        properties.put("spring.jpa.properties.hibernate.default_schema", schema);
        properties.put("spring.flyway.enabled", "true");
        properties.put("spring.flyway.schemas", schema);
        properties.put("spring.flyway.default-schema", schema);
        properties.put("spring.data.redis.host", redis.getHost());
        properties.put("spring.data.redis.port", redis.getMappedPort(6379));
        properties.put("spring.data.redis.repositories.enabled", "false");
        properties.put("spring.rabbitmq.host", rabbitmq.getHost());
        properties.put("spring.rabbitmq.port", rabbitmq.getAmqpPort());
        properties.put("spring.rabbitmq.username", RABBIT_USER);
        properties.put("spring.rabbitmq.password", RABBIT_PASSWORD);
        properties.put("spring.rabbitmq.virtual-host", RABBIT_VHOST);
        properties.put("spring.rabbitmq.publisher-confirm-type", "correlated");
        properties.put("spring.rabbitmq.publisher-returns", "true");
        properties.put("spring.rabbitmq.template.mandatory", "true");
        properties.put("spring.mail.host", "127.0.0.1");
        properties.put("spring.mail.port", "1");
        properties.put("liteworkflow.security.jwt.secret", JWT_SECRET);
        properties.put("liteworkflow.security.jwt.issuer", JWT_ISSUER);
        properties.put("springdoc.api-docs.enabled", "false");
        properties.put("management.health.rabbit.enabled", "false");
        properties.put("management.health.redis.enabled", "false");
        properties.put("logging.level.root", "WARN");
        return properties;
    }

    private String register(String email, String displayName, String password) throws Exception {
        return success(request(
                        "POST",
                        "/api/v1/auth/register",
                        null,
                        Map.of("email", email, "displayName", displayName, "password", password)), 201)
                .path("data")
                .path("accessToken")
                .asText();
    }

    private void awaitCoreProjection(String accessToken) throws Exception {
        long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
        HttpResult last = null;
        while (System.nanoTime() < deadline) {
            last = request("GET", "/api/v1/users/me", accessToken, null);
            if (last.status() == 200 && "OK".equals(last.body().path("code").asText())) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("User-directory projection did not become ready: " + last);
    }

    private JsonNode search(String token, UUID workspaceId, String keyword) throws Exception {
        String path = "/api/v1/users/search?keyword=" + encode(keyword)
                + "&contextType=WORKSPACE&contextId=" + workspaceId;
        JsonNode records = success(request("GET", path, token, null), 200)
                .path("data")
                .path("records");
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private JsonNode findRecord(JsonNode records, UUID userId) {
        for (JsonNode record : records) {
            if (userId.toString().equals(record.path("userId").asText())) {
                return record;
            }
        }
        throw new AssertionError("Member not found in response: " + userId);
    }

    private HttpResult request(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(gatewayBaseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json");
            request.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }
        HttpResponse<String> response = httpClient.send(
                request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode responseBody = response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());
        return new HttpResult(response.statusCode(), responseBody);
    }

    private JsonNode success(HttpResult result, int expectedStatus) {
        assertThat(result.status()).as("HTTP response: %s", result.body()).isEqualTo(expectedStatus);
        assertThat(result.body().path("code").asText()).as("API response: %s", result.body()).isEqualTo("OK");
        return result.body();
    }

    private void assertMigrationApplied(String schema, String script) throws Exception {
        try (var connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "select count(*) from " + schema
                                + ".flyway_schema_history where success and script = ?")) {
            statement.setString(1, script);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    private int port(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private void closeResources() {
        close(gatewayContext);
        close(identityContext);
        close(coreContext);
        Stream.of(redis, rabbitmq, postgres)
                .filter(GenericContainer::isRunning)
                .forEach(GenericContainer::stop);
    }

    private void close(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }

    private static DockerImageName compatiblePostgresImage() {
        return image(
                        "liteworkflow.smoke.postgres-image",
                        "pgvector/pgvector:0.8.2-pg16-trixie")
                .asCompatibleSubstituteFor("postgres");
    }

    private static DockerImageName image(String property, String defaultImage) {
        return DockerImageName.parse(System.getProperty(property, defaultImage));
    }

    private static String servletSecurityExclusions() {
        return String.join(",",
                "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
                "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
                "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayFunctionAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayNoLoadBalancerClientAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayReactiveLoadBalancerClientAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayResilience4JCircuitBreakerAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayStreamAutoConfiguration",
                "org.springframework.cloud.gateway.config.LocalResponseCacheAutoConfiguration",
                "org.springframework.cloud.gateway.config.SimpleUrlHandlerMappingGlobalCorsAutoConfiguration",
                "org.springframework.cloud.gateway.discovery.GatewayDiscoveryClientAutoConfiguration");
    }

    private static String migrationLocation(String service) {
        return "filesystem:" + projectRoot()
                .resolve("services")
                .resolve(service)
                .resolve("src/main/resources/db/migration")
                .toAbsolutePath();
    }

    private static Path projectRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isDirectory(directory.resolve("services/core-service"))
                    && Files.isDirectory(directory.resolve("services/identity-service"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Could not locate the liteworkflow repository root");
    }

    private static String[] commandLine(Map<String, Object> properties) {
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Class<?> applicationClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Service application class is not on the smoke-test classpath: " + name, exception);
        }
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
