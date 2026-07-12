package com.liteworkflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.ai.config.AiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiConfigurationValidationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class, RestClientAutoConfiguration.class))
            .withUserConfiguration(AiConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=test",
                    "liteworkflow.ai.provider=openai",
                    "liteworkflow.ai.core-service-url=http://localhost:8082",
                    "liteworkflow.ai.internal-token=internal-test-token");

    @Test
    void missingBaseUrlFailsContextStartup() {
        assertInvalid("liteworkflow.ai.api-key=sk-valid", "liteworkflow.ai.chat-model=gpt-valid");
    }

    @Test
    void missingApiKeyFailsContextStartup() {
        assertInvalid("liteworkflow.ai.base-url=https://example.test", "liteworkflow.ai.chat-model=gpt-valid");
    }

    @Test
    void missingChatModelFailsContextStartup() {
        assertInvalid("liteworkflow.ai.base-url=https://example.test", "liteworkflow.ai.api-key=sk-valid");
    }

    @Test
    void unresolvedAndReplaceMeValuesFailContextStartup() {
        assertInvalid(
                "liteworkflow.ai.base-url=https://example.test",
                "liteworkflow.ai.api-key=${LITEWORKFLOW_AI_API_KEY}",
                "liteworkflow.ai.chat-model=gpt-valid");
        assertInvalid(
                "liteworkflow.ai.base-url=https://example.test",
                "liteworkflow.ai.api-key=sk-valid",
                "liteworkflow.ai.chat-model=replace_me_model");
    }

    @Test
    void completeExternalApiConfigurationStarts() {
        validContext().run(result -> assertThat(result).hasNotFailed());
    }

    private void assertInvalid(String... properties) {
        context.withPropertyValues(properties).run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasMessageContaining("liteworkflow.ai");
        });
    }

    private ApplicationContextRunner validContext() {
        return context.withPropertyValues(
                "liteworkflow.ai.provider=openai",
                "liteworkflow.ai.base-url=https://example.test",
                "liteworkflow.ai.api-key=sk-valid",
                "liteworkflow.ai.chat-model=gpt-valid");
    }
}
