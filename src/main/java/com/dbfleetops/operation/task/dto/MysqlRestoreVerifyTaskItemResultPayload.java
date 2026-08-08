package com.dbfleetops.operation.task.dto;

public record MysqlRestoreVerifyTaskItemResultPayload(String tableName, Boolean existsInRestoredDb,
        Long rowCount, String status, String message) {
}
