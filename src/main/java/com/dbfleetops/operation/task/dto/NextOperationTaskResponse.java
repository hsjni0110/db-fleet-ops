package com.dbfleetops.operation.task.dto;

import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;

import java.time.LocalDateTime;

public record NextOperationTaskResponse(boolean hasTask, Long taskId, OperationTaskType taskType,
        String parametersJson, Long credentialId, Integer executionAttempt,
        LocalDateTime leaseExpiresAt) {
    public static NextOperationTaskResponse from(OperationTask task) {
        return new NextOperationTaskResponse(true, task.getId(), task.getTaskType(),
                task.getParametersJson(), task.getCredentialId(), task.getExecutionAttempt(),
                task.getLeaseExpiresAt());
    }

    public static NextOperationTaskResponse empty() {
        return new NextOperationTaskResponse(false, null, null, null, null, null, null);
    }

    public NextOperationTaskResponse(boolean hasTask, Long taskId, OperationTaskType taskType,
            String parametersJson, Integer executionAttempt, LocalDateTime leaseExpiresAt) {
        this(hasTask, taskId, taskType, parametersJson, null, executionAttempt, leaseExpiresAt);
    }
}
