package com.dbfleetops.operation.dto;

public record MysqlBackupTaskResultPayload(String status, String backupFile, Long fileSizeBytes,
        String checksumSha256, String createdAt, String message) {
}
