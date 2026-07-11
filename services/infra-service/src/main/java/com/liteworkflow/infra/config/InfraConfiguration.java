package com.liteworkflow.infra.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InfraConfiguration {

    @Bean
    Clock infraClock() {
        return Clock.systemUTC();
    }
}
