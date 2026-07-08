package com.dbfleetops.operation.dto;

import java.util.List;

public record MysqlRestoreVerifyTaskPayload(Long operationJobId, Long databaseId, Long backupTaskId,
        String sourceDatabaseName, String backupFile, String host, Integer port, String username,
        String password, String temporaryDatabaseName, List<String> expectedTables,
        Boolean verifyRowCount, Boolean cleanup) {
}
