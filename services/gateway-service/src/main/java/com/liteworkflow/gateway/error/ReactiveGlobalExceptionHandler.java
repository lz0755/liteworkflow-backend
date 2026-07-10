package com.liteworkflow.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.common.core.error.ErrorCode;
import com.liteworkflow.common.core.trace.TraceConstants;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class ReactiveGlobalExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReactiveGlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public ReactiveGlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        ErrorCode errorCode;
        String message;
        if (exception instanceof BizException bizException) {
            errorCode = bizException.errorCode();
            message = bizException.getMessage();
        } else {
            errorCode = CommonErrorCode.INTERNAL_ERROR;
            message = errorCode.defaultMessage();
            log.error("Unhandled gateway request failure", exception);
        }

        String traceId = exchange.getAttribute(TraceConstants.REACTOR_CONTEXT_KEY);
        ApiResponse<Void> response = new ApiResponse<>(
                errorCode.code(), message, null, traceId, Instant.now());
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatusCode.valueOf(errorCode.httpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(response);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException serializationFailure) {
            return Mono.error(serializationFailure);
        }
    }
}
