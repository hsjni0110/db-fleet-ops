package com.dbfleetops.backup.dto;

import java.util.List;

/** Backup 영역이 해석하고 저장하는 복원 검증 결과입니다. */
public record RestoreVerificationResult(String status, Long operationJobId, Long databaseId,
        Long backupTaskId, String sourceDatabaseName, String backupFile,
        String temporaryDatabaseName, Integer restoredTableCount, Integer checkedTableCount,
        Long totalRowCount, List<RestoreVerificationItemResult> items, String message,
        String errorCode, String errorMessage) {
    public boolean isVerified() { return "VERIFIED".equalsIgnoreCase(status); }
    public boolean isCleanupFailed() { return "CLEANUP_FAILED".equalsIgnoreCase(status); }
}
