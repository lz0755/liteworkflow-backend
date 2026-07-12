package com.liteworkflow.common.web.trace;

import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.core.trace.TraceIds;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Propagates the current trace to synchronous service-to-service HTTP clients. */
public final class TraceIdClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String traceId = TraceIds.current();
        if (traceId == null) {
            traceId = TraceIds.safeOrNull(request.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
        }
        if (traceId == null) {
            traceId = TraceIds.resolve(null);
        }
        request.getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        return execution.execute(request, body);
    }
}
