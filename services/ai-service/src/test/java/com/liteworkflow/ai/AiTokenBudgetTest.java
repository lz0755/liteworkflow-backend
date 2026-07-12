package com.liteworkflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.ai.application.AiProviderClient;
import com.liteworkflow.ai.config.AiProperties;
import com.liteworkflow.ai.dto.response.GenerateIssuesSuggestion;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

class AiTokenBudgetTest {

    private final AiProviderClient provider = provider();

    @Test
    void textBudgetIncludesSystemHistoryUserAndMaximumOutput() {
        long userOnly = provider.textTokenBudget("", List.of(), "user");
        long complete = provider.textTokenBudget(
                "system instructions that must be counted",
                List.of(
                        new UserMessage("historical user message"),
                        new AssistantMessage("historical assistant response")),
                "user");

        assertThat(userOnly).isGreaterThanOrEqualTo(100);
        assertThat(complete).isGreaterThan(userOnly);
    }

    @Test
    void structuredBudgetIncludesGeneratedJsonSchema() {
        long plain = provider.textTokenBudget("system", List.of(), "generate issues");
        long structured = provider.structuredTokenBudget(
                "system", "generate issues", GenerateIssuesSuggestion.class);

        assertThat(structured).isGreaterThan(plain);
    }

    private static AiProviderClient provider() {
        AiProperties properties = new AiProperties();
        properties.setMaxOutputTokens(100);
        return new AiProviderClient(
                mock(ChatClient.class),
                properties,
                Validation.buildDefaultValidatorFactory().getValidator(),
                new ObjectMapper());
    }
}
