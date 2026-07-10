package com.liteworkflow.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.error.ErrorCode;
import com.liteworkflow.common.core.trace.TraceConstants;
import java.time.Instant;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public final class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode, String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        String traceId = exchange.getAttribute(TraceConstants.REACTOR_CONTEXT_KEY);
        ApiResponse<Void> body = new ApiResponse<>(
                errorCode.code(), message, null, traceId, Instant.now());
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(errorCode.httpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }
}
