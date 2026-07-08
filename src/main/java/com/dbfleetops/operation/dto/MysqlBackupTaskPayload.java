package com.dbfleetops.operation.dto;

import java.util.List;

public record MysqlBackupTaskPayload(Long operationJobId, Long databaseId, String databaseName,
        String host, Integer port, String username, String password, String backupType,
        Boolean compression, Boolean verifyAfterBackup, List<String> expectedTables,
        Boolean verifyRowCount, Boolean cleanup) {

    public boolean shouldVerifyAfterBackup() {
        return verifyAfterBackup == null || verifyAfterBackup;
    }

    public boolean shouldVerifyRowCount() {
        return verifyRowCount == null || verifyRowCount;
    }

    public boolean shouldCleanup() {
        return cleanup == null || cleanup;
    }
}
