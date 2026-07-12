package com.liteworkflow.common.mq.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.mq.trace.RabbitMdcBeanPostProcessor;
import com.liteworkflow.common.mq.trace.RabbitTraceMessagePostProcessor;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(Jackson2JsonMessageConverter.class)
public class CommonRabbitAutoConfiguration {

    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnMissingBean(MessageConverter.class)
    MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplateCustomizer traceRabbitTemplateCustomizer() {
        RabbitTraceMessagePostProcessor processor = new RabbitTraceMessagePostProcessor();
        return template -> template.addBeforePublishPostProcessors(processor);
    }

    @Bean
    static RabbitMdcBeanPostProcessor rabbitMdcBeanPostProcessor() {
        return new RabbitMdcBeanPostProcessor();
    }
}
