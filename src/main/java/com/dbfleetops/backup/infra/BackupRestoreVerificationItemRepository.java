package com.dbfleetops.backup.infra;

import com.dbfleetops.backup.domain.BackupRestoreVerificationItem;
import com.dbfleetops.backup.domain.BackupRestoreVerificationItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupRestoreVerificationItemRepository
        extends JpaRepository<BackupRestoreVerificationItem, Long> {

    List<BackupRestoreVerificationItem> findByVerificationIdOrderByTableNameAsc(
            Long verificationId);

    List<BackupRestoreVerificationItem> findByVerificationIdAndStatusOrderByTableNameAsc(
            Long verificationId, BackupRestoreVerificationItemStatus status);

    Optional<BackupRestoreVerificationItem> findByVerificationIdAndTableName(Long verificationId,
            String tableName);
}
