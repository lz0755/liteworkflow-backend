package com.liteworkflow.core;

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
class CoreServiceSmokeTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void startsAndReportsHealthyWithTraceHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TraceConstants.TRACE_ID_HEADER, "core-smoke-trace");
        ResponseEntity<String> response = http.exchange(
                "/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER))
                .isEqualTo("core-smoke-trace");
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
