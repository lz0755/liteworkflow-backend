package com.liteworkflow.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

class RagIndexServiceTest {

    @Test
    void duplicateAndOutOfOrderEventsDoNotCallEmbeddingApi() {
        VectorStore vectors = mock(VectorStore.class);
        RagIndexStore store = mock(RagIndexStore.class);
        RagIndexService service = new RagIndexService(vectors, store);
        RagSourceEvent event = event(2, List.of("current"));
        when(store.claim(event, false)).thenReturn(RagIndexStore.Claim.DUPLICATE);

        assertThat(service.index(event)).isEqualTo(RagIndexService.Result.DUPLICATE);
        org.mockito.Mockito.verifyNoInteractions(vectors);

        RagSourceEvent stale = event(1, List.of("old"));
        when(store.claim(stale, false)).thenReturn(RagIndexStore.Claim.STALE);
        assertThat(service.index(stale)).isEqualTo(RagIndexService.Result.STALE);
        org.mockito.Mockito.verifyNoInteractions(vectors);
    }

    @Test
    void embeddingFailureMarksSingleJobFailedAndRemainsRetryable() {
        VectorStore vectors = mock(VectorStore.class);
        RagIndexStore store = mock(RagIndexStore.class);
        RagIndexService service = new RagIndexService(vectors, store);
        RagSourceEvent event = event(3, List.of("one", "two"));
        when(store.claim(event, false)).thenReturn(RagIndexStore.Claim.PROCESS);
        doThrow(new IllegalStateException("embedding unavailable")).when(vectors).add(anyList());

        assertThatThrownBy(() -> service.index(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("embedding unavailable");
        verify(store).markFailed(org.mockito.ArgumentMatchers.eq(event.eventId()),
                org.mockito.ArgumentMatchers.any(IllegalStateException.class));
    }

    @Test
    void exhaustedClaimRejectsMessageForDeadLettering() {
        VectorStore vectors = mock(VectorStore.class);
        RagIndexStore store = mock(RagIndexStore.class);
        RagIndexService service = new RagIndexService(vectors, store);
        RagSourceEvent event = event(3, List.of("content"));
        when(store.claim(event, true)).thenReturn(RagIndexStore.Claim.EXHAUSTED);

        assertThatThrownBy(() -> service.index(event, true))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("attempts exhausted");
        org.mockito.Mockito.verifyNoInteractions(vectors);
    }

    private static RagSourceEvent event(long version, List<String> chunks) {
        return new RagSourceEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RagSourceType.DOCUMENT, UUID.randomUUID(), version, "Document", chunks, false);
    }
}
