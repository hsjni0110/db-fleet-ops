package com.dbfleetops.backup.dto;

import com.dbfleetops.backup.domain.BackupRestoreVerification;
import com.dbfleetops.backup.domain.BackupRestoreVerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record BackupRestoreVerificationResponse(Long id, Long operationJobId, Long backupTaskId,
        Long restoreVerifyTaskId, Long databaseId, String sourceDatabaseName, String backupFile,
        String temporaryDatabaseName, BackupRestoreVerificationStatus status,
        Integer restoredTableCount, Integer checkedTableCount, Long totalRowCount, String errorCode,
        String errorMessage, LocalDateTime startedAt, LocalDateTime completedAt,
        LocalDateTime createdAt, List<BackupRestoreVerificationItemResponse> items) {

    public static BackupRestoreVerificationResponse from(BackupRestoreVerification verification,
            List<BackupRestoreVerificationItemResponse> items) {
        return new BackupRestoreVerificationResponse(verification.getId(),
                verification.getOperationJobId(), verification.getBackupTaskId(),
                verification.getRestoreVerifyTaskId(), verification.getDatabaseId(),
                verification.getSourceDatabaseName(), verification.getBackupFile(),
                verification.getTemporaryDatabaseName(), verification.getStatus(),
                verification.getRestoredTableCount(), verification.getCheckedTableCount(),
                verification.getTotalRowCount(), verification.getErrorCode(),
                verification.getErrorMessage(), verification.getStartedAt(),
                verification.getCompletedAt(), verification.getCreatedAt(), items);
    }
}
