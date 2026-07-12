package com.liteworkflow.ai.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class TestAiConfiguration {

    @Bean
    FakeChatModel fakeChatModel() {
        return new FakeChatModel();
    }

    @Bean
    ChatClient testChatClient(FakeChatModel model) {
        return ChatClient.create(model);
    }
}
