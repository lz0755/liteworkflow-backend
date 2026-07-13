package com.liteworkflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS ai",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.schemas=ai",
        "spring.flyway.default-schema=ai",
        "spring.flyway.locations=classpath:db/ai-migration",
        "spring.ai.model.embedding=none",
        "spring.ai.vectorstore.type=none",
        "liteworkflow.ai.rag.enabled=false",
        "spring.ai.retry.max-attempts=2",
        "spring.ai.retry.backoff.initial-interval=10ms",
        "spring.ai.retry.backoff.max-interval=20ms",
        "spring.ai.retry.backoff.multiplier=2",
        "debug=false",
        "LITEWORKFLOW_AI_CONNECT_TIMEOUT=100ms",
        "LITEWORKFLOW_AI_REQUEST_TIMEOUT=120ms",
        "LITEWORKFLOW_AI_MAX_OUTPUT_TOKENS=100",
        "LITEWORKFLOW_AI_DAILY_REQUEST_LIMIT=100",
        "LITEWORKFLOW_AI_DAILY_TOKEN_LIMIT=100000",
        "JWT_SECRET=c3VwZXItc2VjcmV0LXRlc3Qta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2ghISE="
})
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DirectOpenAiIntegrationTest {

    private static final String API_KEY = "sk-integration-secret-123456789";
    private static final String INTERNAL_TOKEN = "internal-integration-token";
    private static final String MODEL = "integration-chat-model";
    private static final MockWebServer SERVER = server();
    private static final MockWebServer CORE = server();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired TestRestTemplate http;
    @Autowired JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void openAiProperties(DynamicPropertyRegistry properties) {
        properties.add("LITEWORKFLOW_AI_BASE_URL", () -> stripTrailingSlash(SERVER.url("/").toString()));
        properties.add("LITEWORKFLOW_AI_API_KEY", () -> API_KEY);
        properties.add("LITEWORKFLOW_AI_CHAT_MODEL", () -> MODEL);
        properties.add("CORE_SERVICE_URL", () -> stripTrailingSlash(CORE.url("/").toString()));
        properties.add("INTERNAL_SERVICE_TOKEN", () -> INTERNAL_TOKEN);
    }

    @AfterAll
    static void stopServer() throws IOException {
        SERVER.shutdown();
        CORE.shutdown();
    }

    @Test
    @Order(1)
    void assistUsesRealOpenAiAdapterAndPersistsConversationMessagesAndUsage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CORE.enqueue(coreSuccess(projectContext(workspaceId, projectId)));
        SERVER.enqueue(success("Use a smaller, measurable scope.", 10, 5));

        ResponseEntity<String> response = post("/api/v1/ai/assist", userId, Map.of(
                "workspaceId", workspaceId,
                "projectId", projectId,
                "message", "How should I scope this issue?"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.path("data").path("suggestion").asText())
                .isEqualTo("Use a smaller, measurable scope.");
        assertThat(body.path("data").path("usage").path("totalTokens").asInt()).isEqualTo(15);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai.ai_conversations WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai.ai_messages m
                JOIN ai.ai_conversations c ON c.id = m.conversation_id
                WHERE c.user_id = ?
                """, Integer.class, userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT total_tokens FROM ai.ai_usage_logs WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(15);
        var request = SERVER.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + API_KEY);
        var coreRequest = CORE.takeRequest(1, TimeUnit.SECONDS);
        assertThat(coreRequest).isNotNull();
        assertThat(coreRequest.getHeader("X-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
    }

    @Test
    @Order(2)
    void structuredIssueOutputMustPassJsonAndDtoSchemaValidation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String suggestion = """
                {"suggestions":[{"title":"Add audit view","description":"Expose read-only audit data",\
                "acceptanceCriteria":["Authorized members can view entries"],"priority":"MEDIUM"}]}
                """.replace("\\\n", "");
        CORE.enqueue(coreSuccess(projectContext(workspaceId, projectId)));
        SERVER.enqueue(success(suggestion, 20, 12));

        ResponseEntity<String> response = post("/api/v1/ai/issues/generate", userId, Map.of(
                "workspaceId", workspaceId,
                "projectId", projectId,
                "goal", "Create an audit view",
                "context", "Read-only MVP",
                "count", 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.path("data").path("suggestion").path("suggestions").get(0)
                .path("title").asText()).isEqualTo("Add audit view");
        var providerRequest = SERVER.takeRequest(1, TimeUnit.SECONDS);
        assertThat(providerRequest).isNotNull();
        assertThat(providerRequest.getBody().readUtf8())
                .contains("Authoritative project context")
                .doesNotContain("Read-only MVP");
    }

    @Test
    @Order(3)
    void provider429IsRetriedInABoundedWayAndMapped() throws Exception {
        int before = SERVER.getRequestCount();
        SERVER.enqueue(error(429, "rate limited"));
        SERVER.enqueue(error(429, "rate limited"));

        ResponseEntity<String> response = post("/api/v1/ai/assist", UUID.randomUUID(), Map.of(
                "message", "retry safely"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_PROVIDER_429");
        assertThat(SERVER.getRequestCount() - before).isEqualTo(2);
    }

    @Test
    @Order(4)
    void provider5xxIsRetriedAndMappedWithoutLeakingItsSecretBodyToLogs() throws Exception {
        String secret = "sk-never-log-this-secret-987654321";
        int before = SERVER.getRequestCount();
        SERVER.enqueue(error(503, "upstream failure " + secret));
        SERVER.enqueue(error(503, "upstream failure " + secret));

        ResponseEntity<String> response = post("/api/v1/ai/assist", UUID.randomUUID(), Map.of(
                "message", "provider failure"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_PROVIDER_502");
        assertThat(SERVER.getRequestCount() - before).isEqualTo(2);
        assertAllLogsExclude(secret);
    }

    @Test
    @Order(5)
    void invalidStructuredJsonIsRejectedAndRecordedAsFailure() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String sentinel = "INVALID_JSON_SECRET_SENTINEL_93f4f0";
        CORE.enqueue(coreSuccess(projectContext(workspaceId, projectId)));
        SERVER.enqueue(success("this is not JSON " + sentinel, 9, 4));

        ResponseEntity<String> response = post("/api/v1/ai/issues/generate", userId, Map.of(
                "workspaceId", workspaceId,
                "projectId", projectId,
                "goal", "Generate one issue",
                "count", 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_OUTPUT_502");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai.ai_usage_logs
                WHERE user_id = ? AND success = FALSE AND error_code = 'AI_OUTPUT_502'
                """, Integer.class, userId)).isEqualTo(1);
        assertAllLogsExclude(sentinel);
    }

    @Test
    @Order(6)
    void dailyQuotaRejectsBeforeCallingProvider() throws Exception {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai.ai_daily_quotas
                    (usage_date, user_id, request_count, token_count, reserved_tokens)
                VALUES (?, ?, 100, 0, 0)
                """, LocalDate.now(), userId);
        int before = SERVER.getRequestCount();

        ResponseEntity<String> response = post("/api/v1/ai/assist", userId, Map.of(
                "message", "must not reach provider"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_DAILY_REQUEST_429");
        assertThat(SERVER.getRequestCount()).isEqualTo(before);
    }

    @Test
    @Order(7)
    void providerReadTimeoutIsMappedToGatewayTimeout() throws Exception {
        int before = SERVER.getRequestCount();
        SERVER.enqueue(success("too late", 1, 1).setBodyDelay(500, TimeUnit.MILLISECONDS));

        ResponseEntity<String> response = post("/api/v1/ai/assist", UUID.randomUUID(), Map.of(
                "message", "time out"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_PROVIDER_504");
        assertThat(SERVER.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    @Order(8)
    void breakdownUsesServerOwnedIssueContext() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        CORE.enqueue(coreSuccess(issueContext(workspaceId, projectId, issueId)));
        SERVER.enqueue(success("""
                {"subtasks":[{"title":"Add endpoint","description":"Expose read-only data",\
                "completionSignal":"Integration test passes"}]}
                """.replace("\\\n", ""), 25, 10));

        ResponseEntity<String> response = post(
                "/api/v1/ai/issues/" + issueId + "/breakdown",
                UUID.randomUUID(),
                Map.of("workspaceId", workspaceId, "projectId", projectId, "maxSubtasks", 3));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(response.getBody()).path("data").path("suggestion")
                .path("subtasks").get(0).path("title").asText()).isEqualTo("Add endpoint");
    }

    @Test
    @Order(9)
    void summarizeUsesServerOwnedIssueAndActivityContext() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        CORE.enqueue(coreSuccess(issueContext(workspaceId, projectId, issueId)));
        SERVER.enqueue(success("""
                {"summary":"Work is progressing","keyPoints":["Endpoint merged"],\
                "risks":[],"nextActions":["Run load test"]}
                """.replace("\\\n", ""), 30, 12));

        ResponseEntity<String> response = post(
                "/api/v1/ai/issues/" + issueId + "/summarize",
                UUID.randomUUID(),
                Map.of("workspaceId", workspaceId, "projectId", projectId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(response.getBody()).path("data").path("suggestion")
                .path("summary").asText()).isEqualTo("Work is progressing");
    }

    @Test
    @Order(10)
    void weeklyReportUsesServerOwnedProjectActivityContext() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CORE.enqueue(coreSuccess(weeklyContext(workspaceId, projectId)));
        SERVER.enqueue(success("""
                {"executiveSummary":"The milestone is on track","achievements":["API merged"],\
                "inProgress":["Load testing"],"risks":[],"nextWeek":["Release candidate"]}
                """.replace("\\\n", ""), 35, 14));

        ResponseEntity<String> response = post(
                "/api/v1/ai/projects/" + projectId + "/weekly-report",
                UUID.randomUUID(),
                Map.of(
                        "workspaceId", workspaceId,
                        "weekStart", "2026-07-06",
                        "weekEnd", "2026-07-12"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(response.getBody()).path("data").path("suggestion")
                .path("executiveSummary").asText()).isEqualTo("The milestone is on track");
    }

    @Test
    @Order(11)
    void forbiddenProjectIsRejectedBeforeProviderCall() throws Exception {
        int before = SERVER.getRequestCount();
        CORE.enqueue(coreFailure(403, "CORE_PROJECT_403"));

        ResponseEntity<String> response = post("/api/v1/ai/issues/generate", UUID.randomUUID(), Map.of(
                "workspaceId", UUID.randomUUID(),
                "projectId", UUID.randomUUID(),
                "goal", "Must be authorized"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_RESOURCE_403");
        assertThat(SERVER.getRequestCount()).isEqualTo(before);
    }

    @Test
    @Order(12)
    void missingIssueIsRejectedBeforeProviderCall() throws Exception {
        int before = SERVER.getRequestCount();
        CORE.enqueue(coreFailure(404, "CORE_ISSUE_404"));

        ResponseEntity<String> response = post(
                "/api/v1/ai/issues/" + UUID.randomUUID() + "/summarize",
                UUID.randomUUID(),
                Map.of("workspaceId", UUID.randomUUID(), "projectId", UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_RESOURCE_404");
        assertThat(SERVER.getRequestCount()).isEqualTo(before);
    }

    @Test
    @Order(13)
    void crossProjectIssueContextIsRejectedBeforeProviderCall() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID requestedProjectId = UUID.randomUUID();
        UUID actualProjectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        int before = SERVER.getRequestCount();
        CORE.enqueue(coreSuccess(issueContext(workspaceId, actualProjectId, issueId)));

        ResponseEntity<String> response = post(
                "/api/v1/ai/issues/" + issueId + "/breakdown",
                UUID.randomUUID(),
                Map.of("workspaceId", workspaceId, "projectId", requestedProjectId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_RESOURCE_404");
        assertThat(SERVER.getRequestCount()).isEqualTo(before);
    }

    @Test
    @Order(14)
    void malformedProviderJsonIsMappedWithoutPersistingAnAssistantMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        SERVER.enqueue(jsonResponse(200, "{not-valid-provider-json"));

        ResponseEntity<String> response = post("/api/v1/ai/assist", userId, Map.of(
                "message", "reject malformed provider payload"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(JSON.readTree(response.getBody()).path("code").asText()).isEqualTo("AI_PROVIDER_502");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai.ai_messages m
                JOIN ai.ai_conversations c ON c.id = m.conversation_id
                WHERE c.user_id = ? AND m.role = 'ASSISTANT'
                """, Integer.class, userId)).isZero();
    }

    private ResponseEntity<String> post(String path, UUID userId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokens.issueAccessToken(new CurrentUser(userId, "test-user", Set.of("USER"))));
        return http.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    private static MockResponse success(String content, int inputTokens, int outputTokens) throws Exception {
        Map<String, Object> message = Map.of("role", "assistant", "content", content);
        Map<String, Object> choice = Map.of("index", 0, "message", message, "finish_reason", "stop");
        Map<String, Object> response = Map.of(
                "id", "chatcmpl-integration",
                "object", "chat.completion",
                "created", 1_700_000_000,
                "model", MODEL,
                "choices", List.of(choice),
                "usage", Map.of(
                        "prompt_tokens", inputTokens,
                        "completion_tokens", outputTokens,
                        "total_tokens", inputTokens + outputTokens));
        return jsonResponse(200, JSON.writeValueAsString(response));
    }

    private static MockResponse error(int status, String message) throws Exception {
        return jsonResponse(status, JSON.writeValueAsString(Map.of(
                "error", Map.of("message", message, "type", "upstream_error", "code", status))));
    }

    private static MockResponse coreSuccess(Object data) throws Exception {
        return jsonResponse(200, JSON.writeValueAsString(Map.of(
                "code", "OK",
                "message", "Success",
                "data", data,
                "traceId", "core-test-trace",
                "timestamp", Instant.now().toString())));
    }

    private static MockResponse coreFailure(int status, String code) throws Exception {
        return jsonResponse(status, JSON.writeValueAsString(Map.of(
                "code", code,
                "message", "Rejected",
                "traceId", "core-test-trace",
                "timestamp", Instant.now().toString())));
    }

    private static Map<String, Object> projectContext(UUID workspaceId, UUID projectId) {
        return Map.of(
                "workspaceId", workspaceId,
                "projectId", projectId,
                "name", "Server-owned project",
                "description", "Authoritative project context");
    }

    private static Map<String, Object> issueContext(UUID workspaceId, UUID projectId, UUID issueId) {
        return Map.of(
                "workspaceId", workspaceId,
                "projectId", projectId,
                "issueId", issueId,
                "title", "Server-owned issue title",
                "description", "Server-owned issue description",
                "activityDigest", "2026-07-10 | ISSUE_UPDATED");
    }

    private static Map<String, Object> weeklyContext(UUID workspaceId, UUID projectId) {
        return Map.of(
                "workspaceId", workspaceId,
                "projectId", projectId,
                "projectName", "Server-owned project",
                "projectDescription", "Authoritative project context",
                "activityDigest", "2026-07-10 | ISSUE_UPDATED");
    }

    private static void assertAllLogsExclude(String secret) throws IOException {
        Path directory = Path.of("logs", "ai-service");
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String content;
                if (path.getFileName().toString().endsWith(".gz")) {
                    try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
                        content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                } else {
                    content = Files.readString(path);
                }
                assertThat(content).as("secret absent from %s", path).doesNotContain(secret);
            }
        }
    }

    private static MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private static MockWebServer server() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
