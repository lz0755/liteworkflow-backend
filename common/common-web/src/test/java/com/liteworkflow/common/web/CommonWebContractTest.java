package com.liteworkflow.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.web.error.GlobalExceptionHandler;
import com.liteworkflow.common.web.trace.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CommonWebContractTest {

    @Test
    void mapsBusinessFailureAndCopiesTraceId() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();

        mvc.perform(get("/failure").header(TraceConstants.TRACE_ID_HEADER, "mvc-contract-trace"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(TraceConstants.TRACE_ID_HEADER, "mvc-contract-trace"))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.NOT_FOUND.code()))
                .andExpect(jsonPath("$.traceId").value("mvc-contract-trace"));
    }

    @RestController
    static class FailureController {

        @GetMapping("/failure")
        void failure() {
            throw new BizException(CommonErrorCode.NOT_FOUND, "Missing test resource");
        }
    }
}
