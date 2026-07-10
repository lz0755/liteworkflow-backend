package com.liteworkflow.common.web.autoconfigure;

import com.liteworkflow.common.core.async.MdcTaskDecorator;
import com.liteworkflow.common.web.error.GlobalExceptionHandler;
import com.liteworkflow.common.web.trace.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
