package com.dbfleetops.operation.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record MysqlRestoreVerifyTaskPayload(Long operationJobId, Long databaseId, Long backupTaskId,
        String sourceDatabaseName, String backupFile, String host, Integer port,
        @JsonIgnore String username, @JsonIgnore String password, String temporaryDatabaseName,
        List<String> expectedTables,
        Boolean verifyRowCount, Boolean cleanup) {
}
