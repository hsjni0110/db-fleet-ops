package com.dbfleetops.operation.dto;

import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;

import java.time.LocalDateTime;

public record OperationTaskResponse(Long taskId, Long agentId, Long operationJobId,
        OperationTaskType taskType, OperationTaskStatus status, String parametersJson,
        String resultPayloadJson, String errorCode, String errorMessage, LocalDateTime startedAt,
        LocalDateTime completedAt, LocalDateTime createdAt) {
    public static OperationTaskResponse from(OperationTask task) {
        return new OperationTaskResponse(task.getId(), task.getAgentId(), task.getOperationJobId(),
                task.getTaskType(), task.getStatus(), task.getParametersJson(),
                task.getResultPayloadJson(), task.getErrorCode(), task.getErrorMessage(),
                task.getStartedAt(), task.getCompletedAt(), task.getCreatedAt());
    }
}
