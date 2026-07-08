package com.dbfleetops.operation.dto;

import java.util.List;

public record MysqlRestoreVerifyTaskResultPayload(String status, Long operationJobId,
        Long databaseId, Long backupTaskId, String sourceDatabaseName, String backupFile,
        String temporaryDatabaseName, Integer restoredTableCount, Integer checkedTableCount,
        Long totalRowCount, String startedAt, String completedAt,
        List<MysqlRestoreVerifyTaskItemResultPayload> items, String message, String errorCode,
        String errorMessage) {

    public boolean isVerified() {
        return "VERIFIED".equalsIgnoreCase(status);
    }

    public boolean isCleanupFailed() {
        return "CLEANUP_FAILED".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }
}
