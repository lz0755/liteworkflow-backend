package com.liteworkflow.infra.file;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByIdAndStatus(UUID id, FileStatus status);
    List<StoredFile> findByPurposeAndScopeIdAndStatusOrderByCreatedAtDesc(
            FilePurpose purpose, UUID scopeId, FileStatus status);
    List<StoredFile> findByStatusOrderByDeletedAtAsc(FileStatus status, Pageable pageable);
    List<StoredFile> findByStatusInOrderByDeletedAtAsc(Collection<FileStatus> statuses, Pageable pageable);
}
