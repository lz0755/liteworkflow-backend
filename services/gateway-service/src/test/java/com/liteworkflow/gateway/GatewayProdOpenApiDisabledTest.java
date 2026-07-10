package com.liteworkflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "debug=false",
        "management.health.redis.enabled=false",
        "liteworkflow.security.jwt.secret=VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS13aXRoLTMyLWJ5dGVzIQ=="
})
class GatewayProdOpenApiDisabledTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void prodProfileDisablesUiApiDocsAndDocumentationProxyRoutes() {
        String token = jwtTokenService.issueAccessToken(new CurrentUser(
                UUID.fromString("99999999-2222-3333-4444-555555555555"), "prod-test", Set.of()));

        WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build()
                .get()
                .uri("/v3/api-docs/swagger-config")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(routeLocator.getRoutes()
                .map(route -> route.getId())
                .filter(id -> id.startsWith("openapi-"))
                .collectList()
                .block()).isEmpty();
    }
}
