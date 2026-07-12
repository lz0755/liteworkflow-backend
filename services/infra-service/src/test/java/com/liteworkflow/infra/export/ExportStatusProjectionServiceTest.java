package com.liteworkflow.infra.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.infra.config.FileStorageProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExportStatusProjectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private final ExportJobRepository jobs = mock(ExportJobRepository.class);
    private final ExportFileRepository files = mock(ExportFileRepository.class);
    private final ExportConsumedEventRepository consumed = mock(ExportConsumedEventRepository.class);
    private final FileStorageProperties storage = new FileStorageProperties();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ExportStatusProjectionService projection = new ExportStatusProjectionService(
            jobs, files, consumed, storage, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void failedOutcomeMarksJobFailedAndRecordsConsumedEventAtomically() {
        ExportJob job = job(ExportFormat.CSV);
        EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event = event(
                job,
                ExportAmqpConfiguration.FAILED,
                Map.of("jobId", job.getId(), "projectId", job.getProjectId(),
                        "failureCode", "EXPORT_PROCESSING_FAILED"));
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThat(projection.consume(event)).isTrue();

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo("EXPORT_PROCESSING_FAILED");
        verify(consumed).save(any(ExportConsumedEvent.class));
        verify(files, never()).save(any());
    }

    @Test
    void completedOutcomeCreatesOnlyMetadataAndMarksJobCompleted() {
        ExportJob job = job(ExportFormat.XLSX);
        String objectKey = ExportObjectNames.objectKey(
                job.getWorkspaceId(), job.getProjectId(), job.getId(), job.getFormat());
        EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event = event(
                job,
                ExportAmqpConfiguration.COMPLETED,
                Map.ofEntries(
                        Map.entry("jobId", job.getId()),
                        Map.entry("projectId", job.getProjectId()),
                        Map.entry("format", "XLSX"),
                        Map.entry("bucket", storage.getS3().getBucket()),
                        Map.entry("objectKey", objectKey),
                        Map.entry("fileName", ExportObjectNames.fileName(
                                job.getProjectId(), job.getId(), job.getFormat())),
                        Map.entry("contentType", ExportFormat.XLSX.contentType()),
                        Map.entry("sizeBytes", 4096L),
                        Map.entry("sha256Hex", "a".repeat(64)),
                        Map.entry("rowCount", 2500L)));
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThat(projection.consume(event)).isTrue();

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        ArgumentCaptor<ExportFile> saved = ArgumentCaptor.forClass(ExportFile.class);
        verify(files).save(saved.capture());
        assertThat(saved.getValue().getObjectKey()).isEqualTo(objectKey);
        assertThat(saved.getValue().getRowCount()).isEqualTo(2500);
        verify(consumed).save(any(ExportConsumedEvent.class));
    }

    @Test
    void duplicateOutcomeIsIgnoredBeforeJobLookup() {
        ExportJob job = job(ExportFormat.CSV);
        EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event = event(
                job,
                ExportAmqpConfiguration.FAILED,
                Map.of("jobId", job.getId(), "projectId", job.getProjectId(),
                        "failureCode", "EXPORT_PROCESSING_FAILED"));
        when(consumed.existsById(event.eventId())).thenReturn(true);

        assertThat(projection.consume(event)).isFalse();

        verify(jobs, never()).findByIdForUpdate(any());
        verify(files, never()).save(any());
    }

    private ExportJob job(ExportFormat format) {
        return new ExportJob(
                UUID.randomUUID(), format, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);
    }

    private EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event(
            ExportJob job, String eventType, Map<String, ?> payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                NOW,
                new EventScope(job.getWorkspaceId(), job.getProjectId(), job.getRequestedBy()),
                job.getId(),
                json.valueToTree(payload),
                Map.of());
    }
}
