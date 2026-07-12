package com.liteworkflow.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.liteworkflow.ai.config.AiProperties;
import com.liteworkflow.ai.domain.AiTokenUsage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.net.http.HttpTimeoutException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

@Component
public class AiProviderClient {

    private final ChatClient chatClient;
    private final StreamingChatModel streamingChatModel;
    private final AiProperties properties;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public AiProviderClient(
            ChatClient chatClient,
            StreamingChatModel streamingChatModel,
            AiProperties properties,
            Validator validator,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.streamingChatModel = streamingChatModel;
        this.properties = properties;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public AiProviderResult<String> text(String systemPrompt, List<Message> history, String userPrompt) {
        ChatResponse response = call(messages(systemPrompt, history, userPrompt));
        String content = content(response);
        if (content == null || content.isBlank()) {
            throw new AiCallFailure(AiErrorCode.INVALID_STRUCTURED_OUTPUT, usage(response), null);
        }
        return new AiProviderResult<>(content, content, usage(response), model(response));
    }

    public Flux<AiProviderStreamChunk> streamText(
            String systemPrompt, List<Message> history, String userPrompt) {
        List<Message> promptMessages = messages(systemPrompt, history, userPrompt);
        return Flux.defer(() -> streamingChatModel.stream(new Prompt(promptMessages)))
                // Apply demand control at the model boundary, before any response transformation.
                .limitRate(1)
                .map(this::streamChunk)
                .onErrorMap(exception -> exception instanceof AiCallFailure
                        ? exception
                        : failure(exception));
    }

    public <T> AiProviderResult<T> structured(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(responseType);
        String schemaPrompt = userPrompt + "\n\nRequired response schema:\n" + converter.getFormat();
        ChatResponse response = call(messages(systemPrompt, List.of(), schemaPrompt));
        String rawContent = content(response);
        AiTokenUsage usage = usage(response);
        try {
            T converted = objectMapper.readerFor(responseType)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .readValue(rawContent);
            Set<ConstraintViolation<T>> violations = validator.validate(converted);
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException("Structured response violated its DTO constraints");
            }
            return new AiProviderResult<>(converted, rawContent, usage, model(response));
        } catch (Exception exception) {
            throw new AiCallFailure(
                    AiErrorCode.INVALID_STRUCTURED_OUTPUT,
                    usage,
                    rawContent == null ? 0 : rawContent.length(),
                    exception);
        }
    }

    public long textTokenBudget(String systemPrompt, List<Message> history, String userPrompt) {
        return estimatedInputTokens(messages(systemPrompt, history, userPrompt))
                + properties.getMaxOutputTokens();
    }

    public <T> long structuredTokenBudget(
            String systemPrompt, String userPrompt, Class<T> responseType) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(responseType);
        String schemaPrompt = userPrompt + "\n\nRequired response schema:\n" + converter.getFormat();
        return estimatedInputTokens(messages(systemPrompt, List.of(), schemaPrompt))
                + properties.getMaxOutputTokens();
    }

    private ChatResponse call(List<Message> messages) {
        try {
            return chatClient.prompt().messages(messages).call().chatResponse();
        } catch (AiCallFailure failure) {
            throw failure;
        } catch (RuntimeException exception) {
            throw failure(exception);
        }
    }

    AiCallFailure failure(Throwable exception) {
        if (exception instanceof AiCallFailure failure) {
            return failure;
        }
        return new AiCallFailure(
                map(exception), AiTokenUsage.ZERO, responseLength(exception), exception);
    }

    private static int responseLength(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException response) {
                return response.getResponseBodyAsByteArray().length;
            }
        }
        return 0;
    }

    private List<Message> messages(String systemPrompt, List<Message> history, String userPrompt) {
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(history);
        messages.add(new UserMessage(userPrompt));
        return List.copyOf(messages);
    }

    private AiErrorCode map(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                if (status == 429) {
                    return AiErrorCode.PROVIDER_RATE_LIMITED;
                }
                if (status >= 500) {
                    return AiErrorCode.PROVIDER_UNAVAILABLE;
                }
            }
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException
                    || isTimeoutResourceAccess(current)
                    || isJdkReadTimeout(current)) {
                return AiErrorCode.PROVIDER_TIMEOUT;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("429") || normalized.contains("too many requests")) {
                    return AiErrorCode.PROVIDER_RATE_LIMITED;
                }
                if (normalized.contains("timed out") || normalized.contains("timeout")) {
                    return AiErrorCode.PROVIDER_TIMEOUT;
                }
            }
        }
        return AiErrorCode.PROVIDER_UNAVAILABLE;
    }

    private static boolean isTimeoutResourceAccess(Throwable failure) {
        if (!(failure instanceof ResourceAccessException)) {
            return false;
        }
        String message = failure.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("timeout");
    }

    private static boolean isJdkReadTimeout(Throwable failure) {
        if (!(failure instanceof IOException)) {
            return false;
        }
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().contains("JdkClientHttpRequest$TimeoutHandler")
                    || frame.getClassName().contains("CompletableFuture$Timeout")) {
                return true;
            }
        }
        return false;
    }

    private String model(ChatResponse response) {
        if (response != null && response.getMetadata() != null
                && response.getMetadata().getModel() != null
                && !response.getMetadata().getModel().isBlank()) {
            return response.getMetadata().getModel();
        }
        return properties.getChatModel();
    }

    private AiProviderStreamChunk streamChunk(ChatResponse response) {
        String finishReason = null;
        if (response != null && response.getResult() != null
                && response.getResult().getMetadata() != null) {
            finishReason = response.getResult().getMetadata().getFinishReason();
        }
        return new AiProviderStreamChunk(
                content(response), usage(response), model(response), finishReason);
    }

    private static String content(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private static AiTokenUsage usage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return AiTokenUsage.ZERO;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return AiTokenUsage.ZERO;
        }
        return new AiTokenUsage(
                value(usage.getPromptTokens()),
                value(usage.getCompletionTokens()),
                value(usage.getTotalTokens()));
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long estimatedInputTokens(List<Message> messages) {
        long tokens = 0;
        for (Message message : messages) {
            String text = message.getText();
            // A token represents at least one encoded byte. Counting UTF-8 bytes is deliberately
            // conservative for both ASCII and CJK without coupling quota safety to one tokenizer.
            tokens += Math.max(1, text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length) + 8L;
        }
        return Math.max(1, tokens);
    }
}
