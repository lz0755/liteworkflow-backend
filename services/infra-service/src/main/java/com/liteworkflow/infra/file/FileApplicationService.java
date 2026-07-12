package com.liteworkflow.infra.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.PutObjectRequest;
import com.liteworkflow.infra.config.FileStorageProperties;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
                ? documentEvent(stored, context, userId) : null;
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
            StoredFile current = active(fileId);
            current.markPendingDelete(clock.instant());
        });
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

    private FileOutboxEvent documentEvent(StoredFile file, AccessContext context, UUID actorId) {
        UUID eventId = UUID.randomUUID();
        var payload = new ProjectDocumentEvent(eventId, "rag.document.index", 1, clock.instant(),
                context.workspaceId(), context.projectId(), file.getId(), actorId, file.getObjectKey(),
                file.getOriginalName(), file.getContentType(), file.getSizeBytes(), file.getSha256Hex());
        try {
            return new FileOutboxEvent(eventId, payload.eventType(), payload.eventType(),
                    json.writeValueAsString(payload), payload.occurredAt());
        } catch (JsonProcessingException exception) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_FAILED, "Document event could not be serialized", exception);
        }
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
