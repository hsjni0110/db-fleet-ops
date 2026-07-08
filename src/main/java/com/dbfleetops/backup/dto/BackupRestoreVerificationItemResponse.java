package com.dbfleetops.backup.dto;

import com.dbfleetops.backup.domain.BackupRestoreVerificationItem;
import com.dbfleetops.backup.domain.BackupRestoreVerificationItemStatus;

import java.time.LocalDateTime;

public record BackupRestoreVerificationItemResponse(Long id, Long verificationId, String tableName,
        Boolean existsInRestoredDb, Long rowCount, BackupRestoreVerificationItemStatus status,
        String message, LocalDateTime createdAt) {

    public static BackupRestoreVerificationItemResponse from(BackupRestoreVerificationItem item) {
        return new BackupRestoreVerificationItemResponse(item.getId(), item.getVerificationId(),
                item.getTableName(), item.getExistsInRestoredDb(), item.getRowCount(),
                item.getStatus(), item.getMessage(), item.getCreatedAt());
    }
}
