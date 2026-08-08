package com.dbfleetops.operation.dto;

import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.domain.ResultReportType;

import java.time.LocalDateTime;

public record OperationTaskResponse(Long taskId, Long agentId, Long operationJobId,
        Long credentialId, OperationTaskType taskType, OperationTaskStatus status, String parametersJson,
        String resultPayloadJson, String errorCode, String errorMessage, LocalDateTime startedAt,
        LocalDateTime completedAt, int executionAttempt, LocalDateTime leaseExpiresAt,
        LocalDateTime lastProgressAt, String resultReportId, ResultReportType resultReportType,
        String resultReportFingerprint, LocalDateTime createdAt) {
    public OperationTaskResponse(Long taskId, Long agentId, Long operationJobId,
            OperationTaskType taskType, OperationTaskStatus status, String parametersJson,
            String resultPayloadJson, String errorCode, String errorMessage,
            LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime createdAt) {
        this(taskId, agentId, operationJobId, null, taskType, status, parametersJson,
                resultPayloadJson, errorCode, errorMessage, startedAt, completedAt, 0,
                null, null, null, null, null, createdAt);
    }
    public static OperationTaskResponse from(OperationTask task) {
        return new OperationTaskResponse(task.getId(), task.getAgentId(), task.getOperationJobId(),
                task.getCredentialId(),
                task.getTaskType(), task.getStatus(), task.getParametersJson(),
                task.getResultPayloadJson(), task.getErrorCode(), task.getErrorMessage(),
                task.getStartedAt(), task.getCompletedAt(), task.getExecutionAttempt(),
                task.getLeaseExpiresAt(), task.getLastProgressAt(), task.getResultReportId(),
                task.getResultReportType(), task.getResultReportFingerprint(), task.getCreatedAt());
    }
}
