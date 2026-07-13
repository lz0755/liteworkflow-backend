package com.liteworkflow.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreRagEventMapperTest {

    @Test
    void issueDeletionAlsoProducesCommentTombstonesWithDistinctEventIds() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        CoreRagSourceClient core = mock(CoreRagSourceClient.class);
        when(core.issue(issueId)).thenReturn(new CoreRagSourceClient.Source(
                workspaceId, projectId, issueId, 4, true, "Deleted issue", null));
        when(core.deletedIssueComments(issueId)).thenReturn(List.of(new CoreRagSourceClient.Source(
                workspaceId, projectId, commentId, 3, true, "Deleted comment", null)));
        String payload = """
                {"eventId":"%s","eventType":"issue.deleted","aggregateId":"%s",
                 "scope":{"workspaceId":"%s","projectId":"%s"}}
                """.formatted(eventId, issueId, workspaceId, projectId);

        List<RagSourceEvent> events = new CoreRagEventMapper(core)
                .map(new ObjectMapper().readTree(payload));

        assertThat(events).hasSize(2);
        assertThat(events.getFirst().sourceType()).isEqualTo(RagSourceType.ISSUE);
        assertThat(events.get(1).sourceType()).isEqualTo(RagSourceType.COMMENT);
        assertThat(events.get(1).sourceId()).isEqualTo(commentId);
        assertThat(events.get(1).deleted()).isTrue();
        assertThat(events).extracting(RagSourceEvent::eventId).doesNotHaveDuplicates();
    }
}
