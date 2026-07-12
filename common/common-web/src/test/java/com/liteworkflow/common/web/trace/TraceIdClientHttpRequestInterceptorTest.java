package com.liteworkflow.common.web.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.common.core.trace.TraceConstants;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class TraceIdClientHttpRequestInterceptorTest {

    private final TraceIdClientHttpRequestInterceptor interceptor =
            new TraceIdClientHttpRequestInterceptor();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void forwardsCurrentTraceAndReplacesAnExistingHeader() throws Exception {
        MDC.put(TraceConstants.TRACE_ID, "outbound-trace");
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://core.test/internal"));
        request.getHeaders().set(TraceConstants.TRACE_ID_HEADER, "stale-trace");
        AtomicReference<HttpHeaders> sentHeaders = new AtomicReference<>();

        interceptor.intercept(request, new byte[0], (sentRequest, body) -> {
            sentHeaders.set(HttpHeaders.readOnlyHttpHeaders(sentRequest.getHeaders()));
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        });

        assertThat(sentHeaders.get().getFirst(TraceConstants.TRACE_ID_HEADER))
                .isEqualTo("outbound-trace");
    }

    @Test
    void createsASafeTraceForBackgroundCallsWithoutMdc() throws Exception {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://core.test/internal"));
        AtomicReference<String> traceId = new AtomicReference<>();

        interceptor.intercept(request, new byte[0], (sentRequest, body) -> {
            traceId.set(sentRequest.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        });

        assertThat(traceId.get()).matches("[a-f0-9]{32}");
        assertThat(MDC.get(TraceConstants.TRACE_ID)).isNull();
    }

    @Test
    void preservesAnExplicitSafeTraceWhenThereIsNoCurrentMdc() throws Exception {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://core.test/internal"));
        request.getHeaders().set(TraceConstants.TRACE_ID_HEADER, "explicit-trace");
        AtomicReference<String> traceId = new AtomicReference<>();

        interceptor.intercept(request, new byte[0], (sentRequest, body) -> {
            traceId.set(sentRequest.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        });

        assertThat(traceId.get()).isEqualTo("explicit-trace");
    }
}
