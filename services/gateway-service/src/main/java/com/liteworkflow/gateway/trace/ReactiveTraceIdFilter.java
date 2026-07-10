package com.liteworkflow.gateway.trace;

import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.core.trace.TraceIds;
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
        String traceId = TraceIds.resolve(
                exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
        exchange.getAttributes().put(TraceConstants.REACTOR_CONTEXT_KEY, traceId);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        logWithTrace(traceId, () -> log.debug(
                "Gateway request started method={} path={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath()));

        return chain.filter(exchange)
                .contextWrite(context -> context.put(TraceConstants.REACTOR_CONTEXT_KEY, traceId))
                .doFinally(signal -> logWithTrace(traceId, () -> log.debug(
                        "Gateway request completed path={} signal={}",
                        exchange.getRequest().getPath(),
                        signal)));
    }

    private static void logWithTrace(String traceId, Runnable action) {
        String previous = MDC.get(TraceConstants.TRACE_ID);
        MDC.put(TraceConstants.TRACE_ID, traceId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove(TraceConstants.TRACE_ID);
            } else {
                MDC.put(TraceConstants.TRACE_ID, previous);
            }
        }
    }
}
