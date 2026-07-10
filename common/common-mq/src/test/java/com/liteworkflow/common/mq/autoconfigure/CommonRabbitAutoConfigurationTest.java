package com.liteworkflow.common.mq.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CommonRabbitAutoConfigurationTest {

    @Test
    void usesApplicationObjectMapperForEventMessages() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonRabbitAutoConfiguration.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageConverter.class);
                    assertThat(context.getBean(MessageConverter.class))
                            .isInstanceOf(Jackson2JsonMessageConverter.class);
                });
    }
}
