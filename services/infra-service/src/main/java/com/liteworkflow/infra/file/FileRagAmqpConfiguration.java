package com.liteworkflow.infra.file;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FileRagAmqpConfiguration {

    @Bean
    TopicExchange fileRagExchange() {
        return new TopicExchange("rag.exchange", true, false);
    }
}
