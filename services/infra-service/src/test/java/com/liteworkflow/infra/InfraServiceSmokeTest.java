package com.liteworkflow.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.common.core.trace.TraceConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
class InfraServiceSmokeTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void startsAndReportsHealthyWithTraceHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TraceConstants.TRACE_ID_HEADER, "infra-smoke-trace");
        ResponseEntity<String> response = http.exchange(
                "/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER))
                .isEqualTo("infra-smoke-trace");
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void openApiDocumentExposesBusinessPathsAndBearerSecurityScheme() {
        ResponseEntity<String> response = http.getForEntity("/v3/api-docs/infra", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("\"openapi\"", "\"bearer-jwt\"", "\"/api/v1/files/upload\"");
    }
}
