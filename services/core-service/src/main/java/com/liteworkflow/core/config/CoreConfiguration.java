package com.liteworkflow.core.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoreProperties.class)
public class CoreConfiguration {

    @Bean
    Clock coreClock() {
        return Clock.systemUTC();
    }
}
