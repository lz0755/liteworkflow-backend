package com.liteworkflow.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.PutObjectRequest;
import com.liteworkflow.core.application.PermissionService;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class IssueExportProcessorTest {

    @TempDir
    java.nio.file.Path tempDirectory;

    private final ConsumedEventRepository consumed = mock(ConsumedEventRepository.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final IssueExportGenerator generator = mock(IssueExportGenerator.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final ExportOutcomeRecorder outcomes = mock(ExportOutcomeRecorder.class);
    private final CoreExportProperties properties = new CoreExportProperties();
    private final IssueExportProcessor processor = new IssueExportProcessor(
            consumed, permissions, generator, storage, properties, outcomes);

    @Test
    void duplicateRequestedEventDoesNotRegenerateOrUpload() {
        EventEnvelope<IssueExportRequestedPayload> event = requestEvent();
        when(consumed.existsById(event.eventId())).thenReturn(true);

        assertThat(processor.process(event)).isFalse();

        verify(permissions, never()).requireProjectMember(any(), any());
        verify(generator, never()).generate(any(), any(), any());
        verify(storage, never()).put(any());
        verify(outcomes, never()).recordCompleted(any(), any());
    }

    @Test
    void uploadsClosedTemporaryFileWithDeterministicKeyAndDeletesIt() throws Exception {
        EventEnvelope<IssueExportRequestedPayload> event = requestEvent();
        java.nio.file.Path path = tempDirectory.resolve("generated.csv");
        Files.writeString(path, "header\r\nvalue\r\n");
        var generated = new GeneratedIssueExport(
                path, IssueExportFormat.CSV, Files.size(path), "0".repeat(64), 1);
        when(generator.generate(JOB_ID, PROJECT_ID, IssueExportFormat.CSV)).thenReturn(generated);
        when(outcomes.recordCompleted(any(), any())).thenReturn(true);

        assertThat(processor.process(event)).isTrue();

        ArgumentCaptor<PutObjectRequest> upload = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(storage).put(upload.capture());
        assertThat(upload.getValue().objectKey())
                .isEqualTo("exports/" + WORKSPACE_ID + "/" + PROJECT_ID + "/" + JOB_ID + ".csv");
        assertThat(upload.getValue().contentLength()).isEqualTo(generated.sizeBytes());
        assertThat(Files.exists(path)).isFalse();
        verify(outcomes).recordCompleted(any(), any(IssueExportCompletedPayload.class));
    }

    private EventEnvelope<IssueExportRequestedPayload> requestEvent() {
        var payload = new IssueExportRequestedPayload(JOB_ID, PROJECT_ID, USER_ID, IssueExportFormat.CSV);
        return new EventEnvelope<>(
                EVENT_ID,
                CoreExportAmqpConfiguration.REQUESTED,
                1,
                Instant.parse("2026-07-12T00:00:00Z"),
                new EventScope(WORKSPACE_ID, PROJECT_ID, USER_ID),
                JOB_ID,
                payload,
                Map.of());
    }

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
}
