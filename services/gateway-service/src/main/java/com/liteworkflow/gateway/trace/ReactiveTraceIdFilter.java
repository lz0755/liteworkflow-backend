package com.liteworkflow.gateway.trace;

import com.liteworkflow.common.core.logging.SensitiveDataSanitizer;
import com.liteworkflow.common.core.trace.MdcScope;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.core.trace.TraceIds;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.gateway.security.JwtAuthenticationWebFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Reactive trace filter kept separate from the Servlet-only common-web module. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ReactiveTraceIdFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ReactiveTraceIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.nanoTime();
        String traceId = TraceIds.resolve(
                exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
        ServerWebExchange trustedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.keySet().removeIf(GatewayHeaders::isInternalIdentityHeader);
                    headers.remove(TraceConstants.TRACE_ID_HEADER);
                    headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
                }))
                .build();
        trustedExchange.getAttributes().put(TraceConstants.REACTOR_CONTEXT_KEY, traceId);
        trustedExchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);

        return chain.filter(trustedExchange)
                .contextWrite(context -> context.put(TraceConstants.REACTOR_CONTEXT_KEY, traceId))
                .doFinally(signal -> logCompletion(trustedExchange, traceId, startedAt, signal.name()));
    }

    private static void logCompletion(
            ServerWebExchange exchange, String traceId, long startedAt, String signal) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(TraceConstants.TRACE_ID, traceId);
        context.put(TraceConstants.REQUEST_METHOD, exchange.getRequest().getMethod().name());
        context.put(
                TraceConstants.REQUEST_PATH,
                SensitiveDataSanitizer.sanitizeSingleLine(exchange.getRequest().getPath().value()));
        CurrentUser currentUser = exchange.getAttribute(JwtAuthenticationWebFilter.CURRENT_USER_ATTRIBUTE);
        if (currentUser != null) {
            context.put(TraceConstants.USER_ID, currentUser.userId().toString());
        }
        try (MdcScope ignored = MdcScope.open(context)) {
            int status = exchange.getResponse().getStatusCode() == null
                    ? ("ON_ERROR".equals(signal) ? 500 : 200)
                    : exchange.getResponse().getStatusCode().value();
            MDC.put(TraceConstants.STATUS, Integer.toString(status));
            MDC.put(
                    TraceConstants.LATENCY_MS,
                    Long.toString(Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L)));
            log.info("Gateway request completed signal={}", signal);
        }
    }
}
