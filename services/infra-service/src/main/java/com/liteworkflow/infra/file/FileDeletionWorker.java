package com.liteworkflow.infra.file;

import com.liteworkflow.common.file.storage.ObjectStorage;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "liteworkflow.storage", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class FileDeletionWorker {
    private final StoredFileRepository files;
    private final ObjectStorage storage;
    public FileDeletionWorker(StoredFileRepository files, ObjectStorage storage) { this.files = files; this.storage = storage; }

    @Scheduled(fixedDelayString = "${liteworkflow.storage.cleanup-delay:10000}")
    @Transactional
    public void clean() {
        List<StoredFile> pending = files.findByStatusInOrderByDeletedAtAsc(
                List.of(FileStatus.PENDING_DELETE, FileStatus.DELETE_FAILED), PageRequest.of(0, 50));
        for (StoredFile file : pending) {
            try {
                storage.delete(file.getObjectKey());
                file.markDeleted();
            } catch (RuntimeException exception) {
                file.markDeleteFailed();
            }
        }
    }
}
