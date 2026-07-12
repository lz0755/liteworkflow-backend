package com.liteworkflow.ai.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    Clock aiClock() {
        return Clock.systemUTC();
    }

    @Bean
    @Profile("!test")
    ChatClient aiChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    @Qualifier("coreAiRestClient")
    RestClient coreAiRestClient(RestClient.Builder builder, AiProperties properties) {
        return builder.clone()
                .baseUrl(properties.getCoreServiceUrl())
                .defaultHeader("X-Internal-Token", properties.getInternalToken())
                .build();
    }

    @Bean
    RestClientCustomizer aiTimeouts(AiProperties properties) {
        return builder -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(properties.getConnectTimeout())
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
            factory.setReadTimeout(properties.getRequestTimeout());
            builder.requestFactory(factory);
        };
    }
}
