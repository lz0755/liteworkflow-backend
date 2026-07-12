package com.liteworkflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.ai.support.FakeChatModel;
import com.liteworkflow.ai.support.TestAiConfiguration;
import com.liteworkflow.ai.application.AiStreamingService;
import com.liteworkflow.ai.dto.request.AssistRequest;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "debug=false",
                "liteworkflow.ai.max-concurrent-streams=1",
                "liteworkflow.ai.stream-concurrency-acquire-timeout=20ms",
                "liteworkflow.ai.stream-idle-timeout=100ms"
        })
@ActiveProfiles("test")
@Import(TestAiConfiguration.class)
class AiSseIntegrationTest {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() { };

    @LocalServerPort int port;
    @Autowired FakeChatModel model;
    @Autowired JwtTokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired AiStreamingService streamingService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        model.clear();
        client = WebTestClient.bindToServer(new ReactorClientHttpConnector())
                .baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Test
    void streamsContextDeltasUsageAndExactlyOneDoneInOrder() {
        model.enqueueStream(Flux.just(
                chunk("Hel", 0, 0, null),
                chunk("lo", 5, 2, "stop")));

        FluxExchangeResult<ServerSentEvent<String>> result = post(UUID.randomUUID());
        assertThat(result.getStatus().is2xxSuccessful()).isTrue();
        assertThat(result.getResponseHeaders().getContentType()).isNotNull();
        assertThat(result.getResponseHeaders().getContentType()
                .isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("no-store, no-transform");
        assertThat(result.getResponseHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");

        StepVerifier.create(result.getResponseBody().collectList())
                .assertNext(events -> {
                    assertThat(names(events))
                            .containsExactly("context", "delta", "delta", "usage", "done");
                    assertThat(data(events.get(1)).path("text").asText()).isEqualTo("Hel");
                    assertThat(data(events.get(2)).path("text").asText()).isEqualTo("lo");
                    assertThat(data(events.get(3)).path("totalTokens").asInt()).isEqualTo(7);
                    assertThat(data(events.get(4)).path("finishReason").asText()).isEqualTo("STOP");
                    assertExactlyOneTerminal(events);
                })
                .verifyComplete();
    }

    @Test
    void backpressureRequestsProviderOneChunkAtATimeWithoutUnboundedDemand() {
        List<Long> requests = new CopyOnWriteArrayList<>();
        model.enqueueStream(Flux.just(
                        chunk("one", 0, 0, null),
                        chunk("two", 0, 0, null),
                        chunk("three", 3, 3, "stop"))
                .doOnRequest(requests::add));

        var events = streamingService.assist(
                UUID.randomUUID(), new AssistRequest(null, null, null, "verify backpressure"));

        StepVerifier.create(events, 0)
                .thenRequest(1)
                .expectNextMatches(event -> "context".equals(event.event()))
                .then(() -> assertThat(requests).isEmpty())
                .thenRequest(1)
                .expectNextMatches(event -> "delta".equals(event.event()))
                .then(() -> assertBoundedRequests(requests))
                .thenRequest(1)
                .expectNextMatches(event -> "delta".equals(event.event()))
                .then(() -> assertBoundedRequests(requests))
                .thenRequest(1)
                .expectNextMatches(event -> "delta".equals(event.event()))
                .thenRequest(1)
                .expectNextMatches(event -> "usage".equals(event.event()))
                .thenRequest(1)
                .expectNextMatches(event -> "done".equals(event.event()))
                .verifyComplete();

        assertBoundedRequests(requests);
        assertThat(requests).isNotEmpty();
    }

    @Test
    void clientDisconnectCancelsUpstreamAndReleasesStreamPermit() throws Exception {
        CountDownLatch upstreamCancelled = new CountDownLatch(1);
        model.enqueueStream(Flux.concat(
                        Flux.just(chunk("partial", 1, 1, null)),
                        Flux.interval(Duration.ofMillis(20))
                                .map(ignored -> chunk(".", 1, 1, null)))
                .doOnCancel(upstreamCancelled::countDown));

        FluxExchangeResult<ServerSentEvent<String>> result = post(UUID.randomUUID());
        StepVerifier.create(result.getResponseBody())
                .expectNextMatches(event -> "context".equals(event.event()))
                .expectNextMatches(event -> "delta".equals(event.event()))
                .thenCancel()
                .verify();

        assertThat(upstreamCancelled.await(2, TimeUnit.SECONDS)).isTrue();

        model.enqueueStream(Flux.just(chunk("after cancel", 2, 1, "stop")));
        StepVerifier.create(post(UUID.randomUUID()).getResponseBody().collectList())
                .assertNext(events -> {
                    assertThat(names(events)).endsWith("usage", "done");
                    assertExactlyOneTerminal(events);
                })
                .verifyComplete();
    }

    @Test
    void idleTimeoutBecomesOneErrorEventAndCancelsUpstream() throws Exception {
        CountDownLatch upstreamCancelled = new CountDownLatch(1);
        model.enqueueStream(Flux.<ChatResponse>never().doOnCancel(upstreamCancelled::countDown));

        StepVerifier.create(post(UUID.randomUUID()).getResponseBody().collectList())
                .assertNext(events -> {
                    assertThat(names(events)).containsExactly("context", "error");
                    assertThat(data(events.get(1)).path("code").asText())
                            .isEqualTo("AI_PROVIDER_504");
                    assertExactlyOneTerminal(events);
                })
                .verifyComplete();
        assertThat(upstreamCancelled.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void upstreamFailureBecomesOneErrorWithoutDone() {
        model.enqueueStream(Flux.concat(
                Flux.just(chunk("before failure", 2, 1, null)),
                Flux.error(new IllegalStateException("provider secret response"))));

        StepVerifier.create(post(UUID.randomUUID()).getResponseBody().collectList())
                .assertNext(events -> {
                    assertThat(names(events)).containsExactly("context", "delta", "error");
                    assertThat(data(events.get(2)).path("code").asText())
                            .isEqualTo("AI_PROVIDER_502");
                    assertThat(events.get(2).data()).doesNotContain("provider secret response");
                    assertExactlyOneTerminal(events);
                })
                .verifyComplete();
    }

    @Test
    void concurrentStreamIsRejectedAndPermitIsReusableAfterDisconnect() throws Exception {
        CountDownLatch upstreamSubscribed = new CountDownLatch(1);
        CountDownLatch upstreamCancelled = new CountDownLatch(1);
        model.enqueueStream(Flux.concat(
                        Flux.just(chunk("held", 1, 1, null)),
                        Flux.interval(Duration.ofMillis(20))
                                .map(ignored -> chunk(".", 1, 1, null)))
                .doOnSubscribe(ignored -> upstreamSubscribed.countDown())
                .doOnCancel(upstreamCancelled::countDown));

        FluxExchangeResult<ServerSentEvent<String>> first = post(UUID.randomUUID());
        Disposable firstConnection = first.getResponseBody().subscribe();
        assertThat(upstreamSubscribed.await(2, TimeUnit.SECONDS)).isTrue();

        StepVerifier.create(post(UUID.randomUUID()).getResponseBody().collectList())
                .assertNext(events -> {
                    assertThat(names(events)).containsExactly("error");
                    assertThat(data(events.getFirst()).path("code").asText())
                            .isEqualTo("AI_STREAM_CONCURRENCY_429");
                    assertExactlyOneTerminal(events);
                })
                .verifyComplete();

        firstConnection.dispose();
        assertThat(upstreamCancelled.await(2, TimeUnit.SECONDS)).isTrue();

        model.enqueueStream(Flux.just(chunk("released", 2, 1, "stop")));
        StepVerifier.create(post(UUID.randomUUID()).getResponseBody().collectList())
                .assertNext(events -> {
                    assertThat(names(events)).endsWith("usage", "done");
                    assertExactlyOneTerminal(events);
                })
                .verifyComplete();
    }

    private FluxExchangeResult<ServerSentEvent<String>> post(UUID userId) {
        String token = tokens.issueAccessToken(new CurrentUser(userId, "sse-user", Set.of("USER")));
        return client.post()
                .uri("/api/v1/ai/assist/stream")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(Map.of("message", "stream a suggestion"))
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_TYPE);
    }

    private JsonNode data(ServerSentEvent<String> event) {
        try {
            return json.readTree(event.data());
        } catch (Exception failure) {
            throw new AssertionError("Invalid SSE JSON data", failure);
        }
    }

    private static List<String> names(List<ServerSentEvent<String>> events) {
        return events.stream().map(ServerSentEvent::event).toList();
    }

    private static void assertExactlyOneTerminal(List<ServerSentEvent<String>> events) {
        long terminals = events.stream()
                .filter(event -> "done".equals(event.event()) || "error".equals(event.event()))
                .count();
        assertThat(terminals).isEqualTo(1);
    }

    private static void assertBoundedRequests(List<Long> requests) {
        assertThat(requests)
                .doesNotContain(Long.MAX_VALUE)
                .allMatch(requested -> requested == 1L);
    }

    private static ChatResponse chunk(
            String text, int inputTokens, int outputTokens, String finishReason) {
        var generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text), generationMetadata)))
                .metadata(ChatResponseMetadata.builder()
                        .id("fake-stream-response")
                        .model("test-fake-model")
                        .usage(new DefaultUsage(inputTokens, outputTokens, inputTokens + outputTokens))
                        .build())
                .build();
    }
}
