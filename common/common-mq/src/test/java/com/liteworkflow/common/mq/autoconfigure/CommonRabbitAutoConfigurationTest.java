package com.liteworkflow.common.mq.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = CommonRabbitAutoConfigurationTest.TestApplication.class,
        properties = "debug=false")
class CommonRabbitAutoConfigurationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageConverter messageConverter;

    @Autowired
    private RabbitTemplateCustomizer rabbitTemplateCustomizer;

    @Test
    void createsJsonConverterAfterJacksonWithoutManuallyRegisteringObjectMapper() {
        assertThat(objectMapper).isNotNull();
        assertThat(messageConverter).isInstanceOf(Jackson2JsonMessageConverter.class);
        assertThat(rabbitTemplateCustomizer).isNotNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
