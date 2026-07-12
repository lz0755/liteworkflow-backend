package com.liteworkflow.ai.config;

import com.liteworkflow.ai.controller.AiCurrentUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AiWebConfiguration implements WebMvcConfigurer {

    private final AiCurrentUserArgumentResolver currentUserResolver;

    public AiWebConfiguration(AiCurrentUserArgumentResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
