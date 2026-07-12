package com.liteworkflow.ai.support;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Test-only model. No main source set or non-test profile can load this class. */
public final class FakeChatModel implements ChatModel {

    private final Queue<String> responses = new ConcurrentLinkedQueue<>();

    public void enqueue(String response) {
        responses.add(response);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String content = responses.poll();
        if (content == null) {
            content = "Fake test suggestion";
        }
        return ChatResponse.builder()
                .generations(java.util.List.of(new Generation(new AssistantMessage(content))))
                .metadata(ChatResponseMetadata.builder()
                        .id("fake-test-response")
                        .model("test-fake-model")
                        .usage(new DefaultUsage(5, 3, 8))
                        .build())
                .build();
    }
}
