package com.liteworkflow.infra.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class InfraWebConfiguration implements WebMvcConfigurer {
    private final InfraCurrentUserArgumentResolver currentUserResolver;
    public InfraWebConfiguration(InfraCurrentUserArgumentResolver resolver) { this.currentUserResolver = resolver; }
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
