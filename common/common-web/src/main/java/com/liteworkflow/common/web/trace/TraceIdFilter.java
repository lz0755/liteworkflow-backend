package com.liteworkflow.common.web.trace;

import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.core.trace.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/** Servlet-only trace filter. WebFlux applications must use a reactive WebFilter. */
public final class TraceIdFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String previousTraceId = MDC.get(TraceConstants.TRACE_ID);
        String traceId = TraceIds.resolve(request.getHeader(TraceConstants.TRACE_ID_HEADER));
        MDC.put(TraceConstants.TRACE_ID, traceId);
        response.setHeader(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousTraceId == null) {
                MDC.remove(TraceConstants.TRACE_ID);
            } else {
                MDC.put(TraceConstants.TRACE_ID, previousTraceId);
            }
        }
    }
}
