package com.liteworkflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.gateway.error.ReactiveGlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ReactiveGlobalExceptionHandlerTest {

    @Test
    void writesSharedErrorContractWithoutServletInfrastructure() {
        ReactiveGlobalExceptionHandler handler = new ReactiveGlobalExceptionHandler(
                new ObjectMapper().findAndRegisterModules());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/failure"));
        exchange.getAttributes().put(TraceConstants.REACTOR_CONTEXT_KEY, "reactive-error-trace");

        handler.handle(exchange, new BizException(CommonErrorCode.NOT_FOUND, "Missing test route")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"COMMON_404\"")
                .contains("\"traceId\":\"reactive-error-trace\"");
    }
}
