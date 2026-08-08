package com.dbfleetops.operation.job.dto;

public record CreateBackupJobRequest(
        String reason,
        String requestedBy
) {
}
