package com.liteworkflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.gateway.trace.GatewayHeaders;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "debug=false",
        "management.health.redis.enabled=false",
        "liteworkflow.security.jwt.secret=VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS13aXRoLTMyLWJ5dGVzIQ==",
        "liteworkflow.gateway.cors.allowed-origins[0]=https://app.example.test"
})
class GatewaySecurityIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static DisposableServer downstream;

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RouteLocator routeLocator;

    @MockitoBean(name = "loginRateLimiter")
    private RedisRateLimiter loginRateLimiter;

    @MockitoBean(name = "userSearchRateLimiter")
    private RedisRateLimiter userSearchRateLimiter;

    @MockitoBean(name = "aiRateLimiter")
    private RedisRateLimiter aiRateLimiter;

    @MockitoBean(name = "sseRateLimiter")
    private RedisRateLimiter sseRateLimiter;

    private WebTestClient client;

    @BeforeAll
    static void startDownstream() {
        downstream = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    copyAsEchoHeader(request.requestHeaders(), response, GatewayHeaders.USER_ID);
                    copyAsEchoHeader(request.requestHeaders(), response, GatewayHeaders.USERNAME);
                    copyAsEchoHeader(request.requestHeaders(), response, GatewayHeaders.USER_ROLES);
                    copyAsEchoHeader(request.requestHeaders(), response, "X-User-Email");
                    copyAsEchoHeader(request.requestHeaders(), response, TraceConstants.TRACE_ID_HEADER);
                    response.header("X-Echo-Path", request.uri());
                    return response.sendString(Mono.just("ok"));
                })
                .bindNow();
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) {
            downstream.disposeNow();
        }
    }

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("liteworkflow.gateway.services.identity", GatewaySecurityIntegrationTest::downstreamUrl);
        registry.add("liteworkflow.gateway.services.core", GatewaySecurityIntegrationTest::downstreamUrl);
        registry.add("liteworkflow.gateway.services.infra", GatewaySecurityIntegrationTest::downstreamUrl);
        registry.add("liteworkflow.gateway.services.ai", GatewaySecurityIntegrationTest::downstreamUrl);
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
        RateLimiter.Response allowed = new RateLimiter.Response(true, Map.of("X-RateLimit-Remaining", "1"));
        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed));
        when(userSearchRateLimiter.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed));
        when(aiRateLimiter.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed));
        when(sseRateLimiter.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed));
    }

    @Test
    void publicWhitelistIsMethodSpecificAndStripsForgedIdentityHeaders() {
        client.post()
                .uri("/api/v1/auth/register")
                .header(GatewayHeaders.USER_ID, "forged-user")
                .header(GatewayHeaders.USERNAME, "forged-name")
                .header(GatewayHeaders.USER_ROLES, "ADMIN")
                .header("X-User-Email", "forged@example.test")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-Echo-" + GatewayHeaders.USER_ID)
                .expectHeader().doesNotExist("X-Echo-" + GatewayHeaders.USERNAME)
                .expectHeader().doesNotExist("X-Echo-" + GatewayHeaders.USER_ROLES)
                .expectHeader().doesNotExist("X-Echo-X-User-Email");

        client.get()
                .uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON_401");
    }

    @Test
    void protectedRouteWithoutTokenUsesUnifiedUnauthorizedResponse() {
        client.get()
                .uri("/api/v1/users/me")
                .header(TraceConstants.TRACE_ID_HEADER, "missing-token-trace")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(TraceConstants.TRACE_ID_HEADER, "missing-token-trace")
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON_401")
                .jsonPath("$.traceId").isEqualTo("missing-token-trace");
    }

    @Test
    void validTokenOverwritesForgedHeadersAndRoutesUserSearchToCore() {
        String token = token();
        client.get()
                .uri("/api/v1/users/search?q=ali")
                .headers(headers -> headers.setBearerAuth(token))
                .header(GatewayHeaders.USER_ID, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .header(GatewayHeaders.USERNAME, "attacker")
                .header(GatewayHeaders.USER_ROLES, "SUPER_ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Echo-" + GatewayHeaders.USER_ID, USER_ID.toString())
                .expectHeader().valueEquals("X-Echo-" + GatewayHeaders.USERNAME, "alice")
                .expectHeader().valueEquals("X-Echo-" + GatewayHeaders.USER_ROLES, "MEMBER,OWNER")
                .expectHeader().valueEquals("X-Echo-Path", "/api/v1/users/search?q=ali");
    }

    @Test
    void invalidTraceIdIsReplacedBeforeForwarding() {
        client.get()
                .uri("/api/v1/users/me")
                .headers(headers -> headers.setBearerAuth(token()))
                .header(TraceConstants.TRACE_ID_HEADER, "invalid trace id with spaces")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(TraceConstants.TRACE_ID_HEADER, "[a-f0-9]{32}")
                .expectHeader().valueMatches("X-Echo-" + TraceConstants.TRACE_ID_HEADER, "[a-f0-9]{32}");
    }

    @Test
    void corsPreflightUsesConfiguredOriginAndAllowsBearerHeader() {
        client.options()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "https://app.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.test")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> assertThat(value).containsIgnoringCase(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void loginRateLimitReturnsTooManyRequests() {
        when(loginRateLimiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(false, Map.of())));

        client.post()
                .uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    @Test
    void swaggerConfigExposesFiveServiceGroupsAndBearerJwtScheme() {
        client.get()
                .uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls.length()").isEqualTo(5)
                .jsonPath("$.urls[?(@.name == 'gateway')]").exists()
                .jsonPath("$.urls[?(@.name == 'identity')]").exists()
                .jsonPath("$.urls[?(@.name == 'core')]").exists()
                .jsonPath("$.urls[?(@.name == 'infra')]").exists()
                .jsonPath("$.urls[?(@.name == 'ai')]").exists();

        client.get()
                .uri("/v3/api-docs/gateway")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.securitySchemes.bearer-jwt.type").isEqualTo("http")
                .jsonPath("$.components.securitySchemes.bearer-jwt.scheme").isEqualTo("bearer");
    }

    @Test
    void sseRouteDisablesResponseTimeoutAndBuffering() {
        client.post()
                .uri("/api/v1/ai/assist/stream")
                .headers(headers -> headers.setBearerAuth(token()))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store, no-transform")
                .expectHeader().valueEquals("X-Accel-Buffering", "no");
        verify(sseRateLimiter).isAllowed("ai-sse", "user:" + USER_ID);

        var sseRoute = routeLocator.getRoutes()
                .filter(route -> route.getId().equals("ai-sse"))
                .blockFirst();
        assertThat(sseRoute).isNotNull();
        assertThat(sseRoute.getMetadata()).containsEntry("response-timeout", -1L);
    }

    @Test
    void nonStreamingAiUsesItsOwnRateLimitBucket() {
        client.post()
                .uri("/api/v1/ai/assist")
                .headers(headers -> headers.setBearerAuth(token()))
                .exchange()
                .expectStatus().isOk();

        verify(aiRateLimiter).isAllowed("ai-api", "user:" + USER_ID);
    }

    private String token() {
        return jwtTokenService.issueAccessToken(new CurrentUser(USER_ID, "alice", Set.of("OWNER", "MEMBER")));
    }

    private static String downstreamUrl() {
        return "http://127.0.0.1:" + downstream.port();
    }

    private static void copyAsEchoHeader(
            io.netty.handler.codec.http.HttpHeaders requestHeaders,
            reactor.netty.http.server.HttpServerResponse response,
            String name) {
        String value = requestHeaders.get(name);
        if (value != null) {
            response.header("X-Echo-" + name, value);
        }
    }
}
