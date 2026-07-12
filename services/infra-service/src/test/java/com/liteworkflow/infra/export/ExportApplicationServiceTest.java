package com.liteworkflow.infra.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.infra.config.FileStorageProperties;
import com.liteworkflow.infra.file.AccessContext;
import com.liteworkflow.infra.file.FileAccessAuthorizer;
import com.liteworkflow.infra.file.FileScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class ExportApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private final ExportJobRepository jobs = mock(ExportJobRepository.class);
    private final ExportFileRepository files = mock(ExportFileRepository.class);
    private final ExportJobStore store = mock(ExportJobStore.class);
    private final FileAccessAuthorizer access = mock(FileAccessAuthorizer.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final FileStorageProperties storageProperties = new FileStorageProperties();
    private final ExportApplicationService service = new ExportApplicationService(
            jobs,
            files,
            store,
            access,
            storage,
            storageProperties,
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void createPersistsJobAndRequestOutboxWithTheHttpTraceId() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(access.authorize(userId, FileScope.PROJECT, projectId, FileAccessAuthorizer.AccessAction.READ))
                .thenReturn(new AccessContext(workspaceId, projectId, null));
        MDC.put(TraceConstants.TRACE_ID, "export-http-trace");

        ExportJobResponse response = service.create(
                userId, new CreateIssueExportRequest(projectId, ExportFormat.CSV));

        ArgumentCaptor<ExportJob> job = ArgumentCaptor.forClass(ExportJob.class);
        ArgumentCaptor<ExportOutboxEvent> event = ArgumentCaptor.forClass(ExportOutboxEvent.class);
        verify(store).create(job.capture(), event.capture());
        assertThat(response.status()).isEqualTo(ExportJobStatus.PENDING);
        assertThat(job.getValue().getId()).isEqualTo(response.id());
        assertThat(event.getValue().getExportJobId()).isEqualTo(response.id());
        assertThat(event.getValue().getTraceId()).isEqualTo("export-http-trace");
        assertThat(event.getValue().getPayloadJson()).doesNotContain("token", "password");
    }

    @Test
    void unauthorizedDownloadStopsBeforeFileMetadataAndObjectStorage() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ExportJob job = new ExportJob(
                UUID.randomUUID(), ExportFormat.CSV, UUID.randomUUID(), projectId, ownerId, NOW);
        job.complete(NOW);
        when(jobs.findByIdAndRequestedBy(job.getId(), ownerId)).thenReturn(Optional.of(job));
        when(access.authorize(ownerId, FileScope.PROJECT, projectId, FileAccessAuthorizer.AccessAction.READ))
                .thenThrow(new BizException(CommonErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.download(ownerId, job.getId()))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(files, never()).findByExportJobId(any());
        verify(storage, never()).get(any());
    }
}
