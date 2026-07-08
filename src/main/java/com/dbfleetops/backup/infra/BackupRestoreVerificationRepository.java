package com.dbfleetops.backup.infra;

import com.dbfleetops.backup.domain.BackupRestoreVerification;
import com.dbfleetops.backup.domain.BackupRestoreVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupRestoreVerificationRepository
        extends JpaRepository<BackupRestoreVerification, Long> {

    Optional<BackupRestoreVerification> findByOperationJobId(Long operationJobId);

    Optional<BackupRestoreVerification> findByRestoreVerifyTaskId(Long restoreVerifyTaskId);

    Optional<BackupRestoreVerification> findFirstByDatabaseIdOrderByCreatedAtDesc(Long databaseId);

    List<BackupRestoreVerification> findTop10ByDatabaseIdOrderByCreatedAtDesc(Long databaseId);

    List<BackupRestoreVerification> findByDatabaseIdAndStatusOrderByCreatedAtDesc(Long databaseId,
            BackupRestoreVerificationStatus status);
}
