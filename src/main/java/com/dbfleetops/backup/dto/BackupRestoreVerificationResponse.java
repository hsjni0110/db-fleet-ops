package com.dbfleetops.backup.dto;

import com.dbfleetops.backup.domain.BackupRestoreVerification;
import com.dbfleetops.backup.domain.BackupRestoreVerificationStatus;
import com.dbfleetops.backup.domain.BusinessVerificationStatus;
import com.dbfleetops.backup.domain.CleanupStatus;
import com.dbfleetops.backup.domain.TechnicalVerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record BackupRestoreVerificationResponse(Long id, Long operationJobId, Long backupTaskId,
        Long restoreVerifyTaskId, Long databaseId, String sourceDatabaseName, String backupFile,
        String temporaryDatabaseName, BackupRestoreVerificationStatus status,
        Integer restoredTableCount, Integer checkedTableCount, Long totalRowCount, String errorCode,
        String errorMessage, LocalDateTime startedAt, LocalDateTime completedAt,
        LocalDateTime createdAt, List<BackupRestoreVerificationItemResponse> items,
        TechnicalVerificationStatus technicalStatus, CleanupStatus cleanupStatus,
        String cleanupErrorCode, String cleanupErrorMessage,
        BusinessVerificationResponse businessVerification) {

    public BackupRestoreVerificationResponse(Long id, Long operationJobId, Long backupTaskId,
            Long restoreVerifyTaskId, Long databaseId, String sourceDatabaseName, String backupFile,
            String temporaryDatabaseName, BackupRestoreVerificationStatus status,
            Integer restoredTableCount, Integer checkedTableCount, Long totalRowCount,
            String errorCode, String errorMessage, LocalDateTime startedAt,
            LocalDateTime completedAt, LocalDateTime createdAt,
            List<BackupRestoreVerificationItemResponse> items) {
        this(id, operationJobId, backupTaskId, restoreVerifyTaskId, databaseId, sourceDatabaseName,
                backupFile, temporaryDatabaseName, status, restoredTableCount, checkedTableCount,
                totalRowCount, errorCode, errorMessage, startedAt, completedAt, createdAt, items,
                deriveTechnicalStatus(status), CleanupStatus.NOT_REQUESTED, null, null,
                new BusinessVerificationResponse(null, BusinessVerificationStatus.NOT_APPLIED, 0,
                        0, List.of()));
    }

    public static BackupRestoreVerificationResponse from(BackupRestoreVerification verification,
            List<BackupRestoreVerificationItemResponse> items,
            List<BusinessRuleResultResponse> ruleResults) {
        TechnicalVerificationStatus technicalStatus = verification.getTechnicalStatus() == null
                ? deriveTechnicalStatus(verification.getStatus()) : verification.getTechnicalStatus();
        BusinessVerificationStatus businessStatus = verification.getBusinessStatus() == null
                ? BusinessVerificationStatus.NOT_APPLIED : verification.getBusinessStatus();
        CleanupStatus cleanupStatus = verification.getCleanupStatus() == null
                ? CleanupStatus.NOT_REQUESTED : verification.getCleanupStatus();
        return new BackupRestoreVerificationResponse(verification.getId(),
                verification.getOperationJobId(), verification.getBackupTaskId(),
                verification.getRestoreVerifyTaskId(), verification.getDatabaseId(),
                verification.getSourceDatabaseName(), verification.getBackupFile(),
                verification.getTemporaryDatabaseName(), verification.getStatus(),
                verification.getRestoredTableCount(), verification.getCheckedTableCount(),
                verification.getTotalRowCount(), verification.getErrorCode(),
                verification.getErrorMessage(), verification.getStartedAt(),
                verification.getCompletedAt(), verification.getCreatedAt(), items, technicalStatus,
                cleanupStatus, verification.getCleanupErrorCode(),
                verification.getCleanupErrorMessage(),
                new BusinessVerificationResponse(
                        verification.getBusinessVerificationProfileKey(), businessStatus,
                        verification.getCheckedRuleCount() == null ? 0
                                : verification.getCheckedRuleCount(),
                        verification.getFailedRuleCount() == null ? 0
                                : verification.getFailedRuleCount(),
                        ruleResults == null ? List.of() : ruleResults));
    }

    public static BackupRestoreVerificationResponse from(BackupRestoreVerification verification,
            List<BackupRestoreVerificationItemResponse> items) {
        return from(verification, items, List.of());
    }

    private static TechnicalVerificationStatus deriveTechnicalStatus(
            BackupRestoreVerificationStatus status) {
        return status == BackupRestoreVerificationStatus.VERIFIED
                || status == BackupRestoreVerificationStatus.CLEANUP_FAILED
                        ? TechnicalVerificationStatus.VERIFIED
                        : TechnicalVerificationStatus.FAILED;
    }
}
