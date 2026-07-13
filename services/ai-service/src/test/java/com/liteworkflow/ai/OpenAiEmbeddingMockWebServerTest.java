package com.liteworkflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResponseErrorHandler;

class OpenAiEmbeddingMockWebServerTest {

    private static final String API_KEY = "embedding-test-secret";
    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void shutdown() throws IOException {
        server.shutdown();
    }

    @Test
    void embeddingSuccessUsesConfiguredEndpointModelDimensionsAndAuthorization() throws Exception {
        server.enqueue(json(200, """
                {"object":"list","data":[{"object":"embedding","index":0,
                "embedding":[0.1,0.2,0.3]}],"model":"embedding-m13",
                "usage":{"prompt_tokens":2,"total_tokens":2}}
                """));

        float[] vector = model(Duration.ofSeconds(1), 1).embed("isolate every project");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/embeddings");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + API_KEY);
        assertThat(request.getBody().readUtf8())
                .contains("\"model\":\"embedding-m13\"", "\"dimensions\":3");
    }

    @ParameterizedTest(name = "Embedding HTTP {0} is bounded and retryable")
    @ValueSource(ints = {429, 503})
    void embedding429And5xxAreRetriedInABoundedWay(int status) {
        int before = server.getRequestCount();
        server.enqueue(json(status, errorBody(status)));
        server.enqueue(json(status, errorBody(status)));

        assertThatThrownBy(() -> model(Duration.ofSeconds(1), 2).embed("retry embedding"))
                .isInstanceOf(TransientAiException.class);
        assertThat(server.getRequestCount() - before).isEqualTo(2);
    }

    @Test
    void embeddingReadTimeoutFailsWithinTheConfiguredBound() {
        int before = server.getRequestCount();
        server.enqueue(json(200, """
                {"object":"list","data":[{"object":"embedding","index":0,
                "embedding":[0.1,0.2,0.3]}],"model":"embedding-m13",
                "usage":{"prompt_tokens":1,"total_tokens":1}}
                """).setBodyDelay(500, TimeUnit.MILLISECONDS));
        long started = System.nanoTime();

        assertThatThrownBy(() -> model(Duration.ofMillis(100), 1).embed("timeout embedding"))
                .isInstanceOf(RuntimeException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        assertThat(server.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void invalidEmbeddingJsonIsRejectedWithoutRetrying() {
        int before = server.getRequestCount();
        server.enqueue(json(200, "{not-valid-json"));

        assertThatThrownBy(() -> model(Duration.ofSeconds(1), 1).embed("invalid json"))
                .isInstanceOf(RuntimeException.class);
        assertThat(server.getRequestCount() - before).isEqualTo(1);
    }

    private OpenAiEmbeddingModel model(Duration timeout, int maxAttempts) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(timeout);
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(stripTrailingSlash(server.url("/").toString()))
                .apiKey(API_KEY)
                .embeddingsPath("/v1/embeddings")
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .responseErrorHandler(new RetryableEmbeddingErrorHandler())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model("embedding-m13")
                .dimensions(3)
                .build();
        RetryTemplate retry = RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .fixedBackoff(10)
                .retryOn(TransientAiException.class)
                .build();
        return new OpenAiEmbeddingModel(
                api, MetadataMode.EMBED, options, retry, ObservationRegistry.NOOP);
    }

    private static String errorBody(int status) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                    "error", Map.of(
                            "message", "embedding unavailable",
                            "type", "upstream_error",
                            "code", status)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private static final class RetryableEmbeddingErrorHandler implements ResponseErrorHandler {
        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return response.getStatusCode().isError();
        }

        @Override
        public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
            int status = response.getStatusCode().value();
            if (status == 429 || status >= 500) {
                throw new TransientAiException("Embedding provider HTTP " + status);
            }
            throw new org.springframework.ai.retry.NonTransientAiException(
                    "Embedding provider HTTP " + status);
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
