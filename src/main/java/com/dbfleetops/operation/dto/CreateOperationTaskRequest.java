package com.dbfleetops.operation.dto;

import com.dbfleetops.operation.domain.OperationTaskType;

public record CreateOperationTaskRequest(Long agentId, Long operationJobId,
        OperationTaskType taskType, String parametersJson) {
}
