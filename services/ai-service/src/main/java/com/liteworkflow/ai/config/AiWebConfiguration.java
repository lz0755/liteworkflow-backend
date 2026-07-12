package com.liteworkflow.ai.config;

import com.liteworkflow.ai.controller.AiCurrentUserArgumentResolver;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AiWebConfiguration implements WebMvcConfigurer {

    private final AiCurrentUserArgumentResolver currentUserResolver;
    private final AsyncTaskExecutor sseTaskExecutor;

    public AiWebConfiguration(
            AiCurrentUserArgumentResolver currentUserResolver,
            @Qualifier("aiSseTaskExecutor") AsyncTaskExecutor sseTaskExecutor) {
        this.currentUserResolver = currentUserResolver;
        this.sseTaskExecutor = sseTaskExecutor;
    }

    @Bean("aiSseTaskExecutor")
    static ThreadPoolTaskExecutor aiSseTaskExecutor(AiProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-sse-write-");
        executor.setCorePoolSize(Math.max(2, properties.getMaxConcurrentStreams()));
        executor.setMaxPoolSize(Math.max(4, properties.getMaxConcurrentStreams() * 2));
        executor.setQueueCapacity(Math.max(8, properties.getMaxConcurrentStreams() * 2));
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(sseTaskExecutor);
        // The provider idle timeout owns stream termination; the servlet container must not race it.
        configurer.setDefaultTimeout(-1);
    }
}
