package com.dbfleetops.operation.dto;

public record CreateBackupJobRequest(
        String reason,
        String requestedBy
) {
}