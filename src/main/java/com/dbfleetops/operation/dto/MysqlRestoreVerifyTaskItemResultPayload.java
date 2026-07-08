package com.dbfleetops.operation.dto;

public record MysqlRestoreVerifyTaskItemResultPayload(String tableName, Boolean existsInRestoredDb,
        Long rowCount, String status, String message) {
}
