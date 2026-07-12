package com.liteworkflow.infra.file;

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
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.PutObjectRequest;
import com.liteworkflow.infra.config.FileStorageProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class FileApplicationServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID issueId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-07-11T12:00:00Z");
    private final StoredFileRepository files = mock(StoredFileRepository.class);
    private final FileOutboxRepository outbox = mock(FileOutboxRepository.class);
    private final FileAccessAuthorizer access = mock(FileAccessAuthorizer.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private FileApplicationService service;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new FileApplicationService(files, outbox, access,
                new FileContentValidator(properties), new ServerObjectKeyFactory(clock), storage,
                properties, new ObjectMapper().findAndRegisterModules(), clock, transactionManager);
    }

    @Test
    void removesOrphanWhenDatabaseWriteFailsAndNeverUsesOriginalNameInKey() {
        when(access.authorize(userId, FileScope.ISSUE, issueId, FileAccessAuthorizer.AccessAction.WRITE))
                .thenReturn(new AccessContext(workspaceId, projectId, issueId));
        when(files.saveAndFlush(any())).thenThrow(new IllegalStateException("database unavailable"));
        var multipart = new MockMultipartFile("file", "customer screenshot.png", "image/png", PNG);

        assertThatThrownBy(() -> service.upload(userId, FilePurpose.ATTACHMENT, issueId, multipart))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(FileErrorCode.FILE_UPLOAD_FAILED));

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(storage).put(put.capture());
        assertThat(put.getValue().objectKey())
                .startsWith("attachments/" + workspaceId + "/" + projectId + "/" + issueId + "/2026/07/")
                .doesNotContain("customer", " ", "..", "\\");
        verify(storage).delete(put.getValue().objectKey());
    }

    @Test
    void blocksUnauthorizedDownloadBeforeObjectStorageIsTouched() {
        var validated = new ValidatedFile(PNG, "avatar.png", "png", "image/png", "0".repeat(64));
        StoredFile file = new StoredFile(UUID.randomUUID(), FilePurpose.ATTACHMENT, issueId,
                new AccessContext(workspaceId, projectId, issueId), "liteworkflow", "attachments/safe/file.png",
                validated, UUID.randomUUID(), now);
        when(files.findByIdAndStatus(file.getId(), FileStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(access.authorize(userId, FileScope.ISSUE, issueId, FileAccessAuthorizer.AccessAction.READ))
                .thenThrow(new BizException(CommonErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.download(userId, file.getId()))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        verify(storage, never()).get(any());
    }

    @Test
    void blocksUnauthorizedDeleteBeforeFileStateOrObjectStorageChanges() {
        var validated = new ValidatedFile(PNG, "attachment.png", "png", "image/png", "0".repeat(64));
        StoredFile file = new StoredFile(UUID.randomUUID(), FilePurpose.ATTACHMENT, issueId,
                new AccessContext(workspaceId, projectId, issueId), "liteworkflow", "attachments/safe/file.png",
                validated, UUID.randomUUID(), now);
        when(files.findByIdAndStatus(file.getId(), FileStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(access.authorize(userId, FileScope.ISSUE, issueId, FileAccessAuthorizer.AccessAction.WRITE))
                .thenThrow(new BizException(CommonErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.delete(userId, file.getId()))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(file.getStatus()).isEqualTo(FileStatus.ACTIVE);
        verify(storage, never()).delete(any());
    }

    @Test
    void projectDocumentOutboxContainsMetadataButNotFileBody() {
        when(access.authorize(userId, FileScope.PROJECT, projectId, FileAccessAuthorizer.AccessAction.WRITE))
                .thenReturn(new AccessContext(workspaceId, projectId, null));
        var multipart = new MockMultipartFile("file", "notes.md", "text/markdown", "private body".getBytes());

        service.upload(userId, FilePurpose.PROJECT_DOCUMENT, projectId, multipart);

        ArgumentCaptor<FileOutboxEvent> event = ArgumentCaptor.forClass(FileOutboxEvent.class);
        verify(outbox).saveAndFlush(event.capture());
        assertThat(event.getValue().getPayloadJson())
                .contains("rag.document.index", projectId.toString(), "notes.md", "sha256Hex")
                .doesNotContain("private body", "bytes", "contentBase64");
    }
}
