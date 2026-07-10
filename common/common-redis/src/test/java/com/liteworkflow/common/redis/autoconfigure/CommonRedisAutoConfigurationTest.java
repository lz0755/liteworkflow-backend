package com.liteworkflow.common.redis.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

class CommonRedisAutoConfigurationTest {

    @Test
    void createsJsonTemplateOnlyWhenConnectionFactoryExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonRedisAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(RedisTemplate.class));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonRedisAutoConfiguration.class))
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context).hasBean("objectRedisTemplate"));
    }
}
