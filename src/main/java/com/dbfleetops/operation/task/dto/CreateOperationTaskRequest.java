package com.dbfleetops.operation.task.dto;

import com.dbfleetops.operation.task.domain.OperationTaskType;

public record CreateOperationTaskRequest(Long agentId, Long operationJobId,
        OperationTaskType taskType, String parametersJson) {
}
