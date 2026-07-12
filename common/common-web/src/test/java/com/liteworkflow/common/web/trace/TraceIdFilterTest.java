package com.liteworkflow.common.web.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.common.core.trace.TraceConstants;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void installsRequestContextWithoutQueryValuesAndRestoresCallingThread() throws Exception {
        MDC.put(TraceConstants.TRACE_ID, "worker-trace");
        MDC.put(TraceConstants.EVENT_ID, "worker-event");
        var request = new MockHttpServletRequest("GET", "/api/v1/users/search");
        request.setQueryString("keyword=complete-private-search-term");
        request.addHeader(TraceConstants.TRACE_ID_HEADER, "request-trace");
        var response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> inside = new AtomicReference<>();

        new TraceIdFilter().doFilter(request, response, (servletRequest, servletResponse) -> {
            inside.set(MDC.getCopyOfContextMap());
            ((MockHttpServletResponse) servletResponse).setStatus(204);
        });

        assertThat(inside.get())
                .containsEntry(TraceConstants.TRACE_ID, "request-trace")
                .containsEntry(TraceConstants.REQUEST_METHOD, "GET")
                .containsEntry(TraceConstants.REQUEST_PATH, "/api/v1/users/search")
                .doesNotContainKey(TraceConstants.EVENT_ID);
        assertThat(inside.get().values()).doesNotContain("complete-private-search-term");
        assertThat(response.getHeader(TraceConstants.TRACE_ID_HEADER)).isEqualTo("request-trace");
        assertThat(MDC.get(TraceConstants.TRACE_ID)).isEqualTo("worker-trace");
        assertThat(MDC.get(TraceConstants.EVENT_ID)).isEqualTo("worker-event");
        assertThat(MDC.get(TraceConstants.REQUEST_PATH)).isNull();
    }
}
