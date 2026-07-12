package com.liteworkflow.common.web.trace;

import com.liteworkflow.common.core.logging.SensitiveDataSanitizer;
import com.liteworkflow.common.core.trace.MdcScope;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.core.trace.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/** Servlet-only trace filter. WebFlux applications must use a reactive WebFilter. */
public final class TraceIdFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = TraceIds.resolve(request.getHeader(TraceConstants.TRACE_ID_HEADER));
        long startedAt = System.nanoTime();
        Map<String, String> requestContext = new LinkedHashMap<>();
        requestContext.put(TraceConstants.TRACE_ID, traceId);
        requestContext.put(TraceConstants.REQUEST_METHOD, request.getMethod());
        requestContext.put(
                TraceConstants.REQUEST_PATH,
                SensitiveDataSanitizer.sanitizeSingleLine(request.getRequestURI()));
        response.setHeader(TraceConstants.TRACE_ID_HEADER, traceId);

        try (MdcScope ignored = MdcScope.open(requestContext)) {
            filterChain.doFilter(request, response);
        } finally {
            // Log only the URI path; query strings can contain complete search terms or secrets.
            try (MdcScope ignored = MdcScope.open(requestContext)) {
                MDC.put(TraceConstants.STATUS, Integer.toString(response.getStatus()));
                MDC.put(TraceConstants.LATENCY_MS, Long.toString(elapsedMillis(startedAt)));
                log.info("HTTP request completed");
            }
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
