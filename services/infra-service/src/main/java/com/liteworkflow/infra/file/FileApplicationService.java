package com.liteworkflow.infra.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.PutObjectRequest;
import com.liteworkflow.common.mq.event.ProjectDocumentEvent;
import com.liteworkflow.infra.config.FileStorageProperties;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileApplicationService {
    private static final Logger log = LoggerFactory.getLogger(FileApplicationService.class);
    private final StoredFileRepository files;
    private final FileOutboxRepository outbox;
    private final FileAccessAuthorizer access;
    private final FileContentValidator validator;
    private final ServerObjectKeyFactory keys;
    private final ObjectStorage storage;
    private final FileStorageProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public FileApplicationService(StoredFileRepository files, FileOutboxRepository outbox,
            FileAccessAuthorizer access, FileContentValidator validator, ServerObjectKeyFactory keys,
            ObjectStorage storage, FileStorageProperties properties, ObjectMapper json, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.files = files; this.outbox = outbox; this.access = access; this.validator = validator;
        this.keys = keys; this.storage = storage; this.properties = properties; this.json = json; this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public FileResponse upload(UUID userId, FilePurpose purpose, UUID resourceId, MultipartFile multipart) {
        if (purpose == null || resourceId == null) throw new BizException(FileErrorCode.FILE_CONTENT_MISMATCH);
        AccessContext context = access.authorize(userId, purpose.scope(), resourceId,
                FileAccessAuthorizer.AccessAction.WRITE);
        ValidatedFile validated = validator.validate(purpose, multipart);
        UUID fileId = UUID.randomUUID();
        String objectKey = keys.create(purpose, context.workspaceId(), context.projectId(), resourceId,
                fileId, validated.extension());
        var now = clock.instant();
        StoredFile stored = new StoredFile(fileId, purpose, resourceId, context, properties.getS3().getBucket(),
                objectKey, validated, userId, now);
        FileOutboxEvent documentEvent = purpose == FilePurpose.PROJECT_DOCUMENT
                ? documentUpsertEvent(stored, context, userId) : null;
        byte[] bytes = validated.bytes();
        storage.put(new PutObjectRequest(objectKey, new ByteArrayInputStream(bytes), bytes.length,
                validated.contentType(), Map.of("file-id", fileId.toString(), "sha256", validated.sha256Hex())));
        try {
            transactions.executeWithoutResult(status -> {
                files.saveAndFlush(stored);
                if (documentEvent != null) outbox.saveAndFlush(documentEvent);
            });
            return FileResponse.from(stored);
        } catch (RuntimeException databaseFailure) {
            compensateOrphan(objectKey, databaseFailure);
            throw new BizException(FileErrorCode.FILE_UPLOAD_FAILED,
                    "File metadata could not be committed; uploaded object was compensated", databaseFailure);
        }
    }

    @Transactional(readOnly = true)
    public FileResponse metadata(UUID userId, UUID fileId) {
        StoredFile file = active(fileId);
        authorize(userId, file, FileAccessAuthorizer.AccessAction.READ);
        return FileResponse.from(file);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> list(UUID userId, FilePurpose purpose, UUID resourceId) {
        access.authorize(userId, purpose.scope(), resourceId, FileAccessAuthorizer.AccessAction.READ);
        return files.findByPurposeAndScopeIdAndStatusOrderByCreatedAtDesc(purpose, resourceId, FileStatus.ACTIVE)
                .stream().map(FileResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FileDownload download(UUID userId, UUID fileId) {
        StoredFile file = active(fileId);
        authorize(userId, file, FileAccessAuthorizer.AccessAction.READ);
        requireConfiguredBucket(file);
        return new FileDownload(file, storage.get(file.getObjectKey()));
    }

    public void delete(UUID userId, UUID fileId) {
        StoredFile file = active(fileId);
        authorize(userId, file, FileAccessAuthorizer.AccessAction.WRITE);
        transactions.executeWithoutResult(status -> {
            StoredFile current = files.findByIdAndStatusForUpdate(fileId, FileStatus.ACTIVE)
                    .orElseThrow(() -> new BizException(FileErrorCode.FILE_NOT_FOUND));
            if (current.getPurpose() == FilePurpose.PROJECT_DOCUMENT) {
                outbox.saveAndFlush(documentDeleteEvent(current, userId));
            }
            current.markPendingDelete(clock.instant());
        });
    }

    public FileResponse replaceProjectDocument(
            UUID userId, UUID projectId, UUID documentId, MultipartFile multipart) {
        AccessContext context = access.authorize(userId, FileScope.PROJECT, projectId,
                FileAccessAuthorizer.AccessAction.WRITE);
        ValidatedFile validated = validator.validate(FilePurpose.PROJECT_DOCUMENT, multipart);
        AtomicReference<String> uploadedKey = new AtomicReference<>();
        try {
            return transactions.execute(status -> {
                StoredFile current = files.findActiveByDocumentIdForUpdate(documentId)
                        .filter(file -> file.getPurpose() == FilePurpose.PROJECT_DOCUMENT
                                && projectId.equals(file.getProjectId()))
                        .orElseThrow(() -> new BizException(FileErrorCode.FILE_NOT_FOUND));
                long sourceVersion = Math.addExact(current.getSourceVersion(), 1);
                UUID fileId = UUID.randomUUID();
                String objectKey = keys.create(FilePurpose.PROJECT_DOCUMENT, context.workspaceId(),
                        context.projectId(), projectId, fileId, validated.extension());
                byte[] bytes = validated.bytes();
                storage.put(new PutObjectRequest(objectKey, new ByteArrayInputStream(bytes), bytes.length,
                        validated.contentType(), Map.of(
                                "file-id", fileId.toString(),
                                "document-id", documentId.toString(),
                                "source-version", Long.toString(sourceVersion),
                                "sha256", validated.sha256Hex())));
                uploadedKey.set(objectKey);
                Instant now = clock.instant();
                StoredFile replacement = new StoredFile(fileId, documentId, sourceVersion,
                        FilePurpose.PROJECT_DOCUMENT, projectId, context, properties.getS3().getBucket(),
                        objectKey, validated, userId, now);
                current.markPendingDelete(now);
                files.flush();
                files.saveAndFlush(replacement);
                outbox.saveAndFlush(documentUpsertEvent(replacement, context, userId));
                return FileResponse.from(replacement);
            });
        } catch (BizException expected) {
            compensateUploadedReplacement(uploadedKey.get(), expected);
            throw expected;
        } catch (RuntimeException failure) {
            compensateUploadedReplacement(uploadedKey.get(), failure);
            throw new BizException(FileErrorCode.FILE_UPLOAD_FAILED,
                    "Document replacement could not be committed", failure);
        }
    }

    private StoredFile active(UUID fileId) {
        return files.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElseThrow(() -> new BizException(FileErrorCode.FILE_NOT_FOUND));
    }

    private void authorize(UUID userId, StoredFile file, FileAccessAuthorizer.AccessAction action) {
        access.authorize(userId, file.getScopeType(), file.getScopeId(), action);
    }

    private void requireConfiguredBucket(StoredFile file) {
        if (!properties.getS3().getBucket().equals(file.getBucket())) {
            throw new BizException(FileErrorCode.FILE_NOT_FOUND);
        }
    }

    private FileOutboxEvent documentUpsertEvent(StoredFile file, AccessContext context, UUID actorId) {
        UUID eventId = UUID.randomUUID();
        var payload = new ProjectDocumentEvent(eventId, "rag.document.upsert", 1, clock.instant(),
                context.workspaceId(), context.projectId(), file.getDocumentId(), file.getId(),
                file.getSourceVersion(), actorId, file.getObjectKey(),
                file.getOriginalName(), file.getContentType(), file.getSizeBytes(), file.getSha256Hex());
        return outboxEvent(payload);
    }

    private FileOutboxEvent documentDeleteEvent(StoredFile file, UUID actorId) {
        UUID eventId = UUID.randomUUID();
        var payload = new ProjectDocumentEvent(eventId, "rag.document.deleted", 1, clock.instant(),
                file.getWorkspaceId(), file.getProjectId(), file.getDocumentId(), file.getId(),
                Math.addExact(file.getSourceVersion(), 1), actorId, null, file.getOriginalName(),
                null, 0, null);
        return outboxEvent(payload);
    }

    private FileOutboxEvent outboxEvent(ProjectDocumentEvent payload) {
        try {
            return new FileOutboxEvent(payload.eventId(), payload.eventType(), payload.eventType(),
                    json.writeValueAsString(payload), payload.occurredAt());
        } catch (JsonProcessingException exception) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_FAILED, "Document event could not be serialized", exception);
        }
    }

    private void compensateUploadedReplacement(String objectKey, RuntimeException failure) {
        if (objectKey != null) compensateOrphan(objectKey, failure);
    }

    private void compensateOrphan(String objectKey, RuntimeException originalFailure) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.error("Failed to compensate orphan object {}", objectKey, cleanupFailure);
        }
    }
}
