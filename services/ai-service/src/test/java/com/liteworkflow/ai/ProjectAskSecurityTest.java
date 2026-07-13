package com.liteworkflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liteworkflow.ai.application.AiApplicationService;
import com.liteworkflow.ai.application.AiConcurrencyGuard;
import com.liteworkflow.ai.application.AiErrorCode;
import com.liteworkflow.ai.application.AiProviderClient;
import com.liteworkflow.ai.application.AiQuotaService;
import com.liteworkflow.ai.application.CoreAiContextClient;
import com.liteworkflow.ai.config.AiProperties;
import com.liteworkflow.ai.domain.AiConversation;
import com.liteworkflow.ai.dto.request.AskProjectRequest;
import com.liteworkflow.ai.infrastructure.AiConversationStore;
import com.liteworkflow.ai.infrastructure.AiUsageStore;
import com.liteworkflow.ai.rag.RagRetrievalService;
import com.liteworkflow.common.core.error.BizException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProjectAskSecurityTest {

    @Test
    void permissionFailureHappensBeforeAnyVectorSearch() {
        Fixture fixture = fixture();
        when(fixture.core.project(fixture.userId, fixture.workspaceId, fixture.projectId))
                .thenThrow(new BizException(AiErrorCode.RESOURCE_FORBIDDEN));

        assertThatThrownBy(() -> fixture.service.askProject(fixture.userId, fixture.projectId,
                new AskProjectRequest(fixture.workspaceId, "secret?")))
                .isInstanceOf(BizException.class)
                .extracting(failure -> ((BizException) failure).errorCode())
                .isEqualTo(AiErrorCode.RESOURCE_FORBIDDEN);
        verify(fixture.retriever, never()).retrieve(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void noReliableContextReturnsExplicitAnswerWithoutCallingChatModel() {
        Fixture fixture = fixture();
        when(fixture.core.project(fixture.userId, fixture.workspaceId, fixture.projectId))
                .thenReturn(new CoreAiContextClient.ProjectContext(
                        fixture.workspaceId, fixture.projectId, "P", "D"));
        when(fixture.retriever.retrieve(fixture.workspaceId, fixture.projectId, "unknown"))
                .thenReturn(new RagRetrievalService.Retrieval(List.of(), List.of()));
        UUID conversationId = UUID.randomUUID();
        when(fixture.conversations.create(fixture.userId, fixture.workspaceId, fixture.projectId,
                "PROJECT_ASK", "unknown"))
                .thenReturn(new AiConversation(conversationId, fixture.userId, fixture.workspaceId,
                        fixture.projectId, "PROJECT_ASK", "unknown", "ACTIVE", Instant.now(), Instant.now()));

        var response = fixture.service.askProject(fixture.userId, fixture.projectId,
                new AskProjectRequest(fixture.workspaceId, "unknown"));

        assertThat(response.answer()).isEqualTo("当前项目资料中没有找到可靠依据。");
        assertThat(response.sources()).isEmpty();
        verify(fixture.provider, never()).text(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture() {
        AiProviderClient provider = mock(AiProviderClient.class);
        AiConversationStore conversations = mock(AiConversationStore.class);
        AiUsageStore usage = mock(AiUsageStore.class);
        AiQuotaService quotas = mock(AiQuotaService.class);
        AiProperties properties = new AiProperties();
        properties.setProvider("openai");
        properties.setChatModel("test-chat");
        AiConcurrencyGuard concurrency = new AiConcurrencyGuard(properties);
        CoreAiContextClient core = mock(CoreAiContextClient.class);
        RagRetrievalService retriever = mock(RagRetrievalService.class);
        ObjectProvider<RagRetrievalService> providerOfRetriever = mock(ObjectProvider.class);
        when(providerOfRetriever.getIfAvailable()).thenReturn(retriever);
        AiApplicationService service = new AiApplicationService(
                provider, conversations, usage, quotas, concurrency, properties, core, providerOfRetriever);
        return new Fixture(service, provider, conversations, core, retriever,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private record Fixture(
            AiApplicationService service,
            AiProviderClient provider,
            AiConversationStore conversations,
            CoreAiContextClient core,
            RagRetrievalService retriever,
            UUID userId,
            UUID workspaceId,
            UUID projectId) {}
}
