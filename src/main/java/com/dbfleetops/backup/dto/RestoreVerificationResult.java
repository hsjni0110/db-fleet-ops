package com.dbfleetops.backup.dto;

import com.dbfleetops.backup.domain.CleanupStatus;
import com.dbfleetops.backup.domain.TechnicalVerificationStatus;

import java.util.List;

/** Backup 영역이 해석하고 저장하는 복원 검증 결과입니다. */
public record RestoreVerificationResult(String status, Long operationJobId, Long databaseId,
        Long backupTaskId, String sourceDatabaseName, String backupFile,
        String temporaryDatabaseName, Integer restoredTableCount, Integer checkedTableCount,
        Long totalRowCount, List<RestoreVerificationItemResult> items, String message,
        String errorCode, String errorMessage, TechnicalVerificationStatus technicalStatus,
        CleanupStatus cleanupStatus, BusinessVerificationResultPayload businessVerification,
        String cleanupErrorCode, String cleanupErrorMessage) {

    public RestoreVerificationResult(String status, Long operationJobId, Long databaseId,
            Long backupTaskId, String sourceDatabaseName, String backupFile,
            String temporaryDatabaseName, Integer restoredTableCount, Integer checkedTableCount,
            Long totalRowCount, List<RestoreVerificationItemResult> items, String message,
            String errorCode, String errorMessage) {
        this(status, operationJobId, databaseId, backupTaskId, sourceDatabaseName, backupFile,
                temporaryDatabaseName, restoredTableCount, checkedTableCount, totalRowCount, items,
                message, errorCode, errorMessage, null, null, null, null, null);
    }

    public boolean isVerified() {
        if (technicalStatus == null && businessVerification == null && cleanupStatus == null) {
            return "VERIFIED".equalsIgnoreCase(status);
        }
        String businessStatus = businessVerification == null ? "NOT_APPLIED"
                : businessVerification.status();
        return technicalStatus == TechnicalVerificationStatus.VERIFIED
                && ("VERIFIED".equalsIgnoreCase(businessStatus)
                        || "NOT_APPLIED".equalsIgnoreCase(businessStatus))
                && cleanupStatus != CleanupStatus.FAILED;
    }
    public boolean isCleanupFailed() { return "CLEANUP_FAILED".equalsIgnoreCase(status); }

    public String finalErrorCode() {
        if (errorCode != null && !errorCode.isBlank()) {
            return errorCode;
        }
        if (businessVerification != null
                && "ERROR".equalsIgnoreCase(businessVerification.status())) {
            return "BUSINESS_VERIFICATION_ERROR";
        }
        if (businessVerification != null
                && "FAILED".equalsIgnoreCase(businessVerification.status())) {
            return "BUSINESS_VERIFICATION_FAILED";
        }
        if (cleanupStatus == CleanupStatus.FAILED) {
            return "CLEANUP_FAILED";
        }
        return "RESTORE_VERIFY_FAILED";
    }
}
