package com.liteworkflow.gateway;

import com.liteworkflow.common.core.trace.TraceConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "debug=false",
        "management.health.redis.enabled=false",
        "liteworkflow.security.jwt.secret=VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS13aXRoLTMyLWJ5dGVzIQ=="
})
class GatewayServiceSmokeTest {

    @LocalServerPort
    private int port;

    @Test
    void startsAndReportsHealthyWithReactiveTraceHeader() {
        WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build()
                .get()
                .uri("/actuator/health")
                .header(TraceConstants.TRACE_ID_HEADER, "gateway-smoke-trace")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(TraceConstants.TRACE_ID_HEADER, "gateway-smoke-trace")
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
