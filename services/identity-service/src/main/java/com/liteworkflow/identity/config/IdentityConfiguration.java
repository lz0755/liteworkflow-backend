package com.liteworkflow.identity.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Bean;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityConfiguration {

    @Bean
    PasswordEncoder identityPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }
}
