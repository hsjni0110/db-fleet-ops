package com.dbfleetops.operation.task.dto;

import com.dbfleetops.operation.task.domain.OperationTask;

import java.time.LocalDateTime;

public record OperationTaskLeaseResponse(Long taskId, int executionAttempt,
        LocalDateTime leaseExpiresAt, LocalDateTime lastProgressAt) {
    public static OperationTaskLeaseResponse from(OperationTask task) {
        return new OperationTaskLeaseResponse(task.getId(), task.getExecutionAttempt(),
                task.getLeaseExpiresAt(), task.getLastProgressAt());
    }
}
