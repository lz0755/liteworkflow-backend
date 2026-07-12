package com.liteworkflow.common.web.autoconfigure;

import com.liteworkflow.common.core.async.MdcTaskDecorator;
import com.liteworkflow.common.web.error.GlobalExceptionHandler;
import com.liteworkflow.common.web.trace.TraceIdClientHttpRequestInterceptor;
import com.liteworkflow.common.web.trace.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
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

    @Bean
    @ConditionalOnMissingBean
    TraceIdClientHttpRequestInterceptor traceIdClientHttpRequestInterceptor() {
        return new TraceIdClientHttpRequestInterceptor();
    }

    @Bean
    RestClientCustomizer traceIdRestClientCustomizer(TraceIdClientHttpRequestInterceptor interceptor) {
        return builder -> builder.requestInterceptor(interceptor);
    }

    @Bean
    RestTemplateCustomizer traceIdRestTemplateCustomizer(TraceIdClientHttpRequestInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }
}
