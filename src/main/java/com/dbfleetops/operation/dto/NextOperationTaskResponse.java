package com.dbfleetops.operation.dto;

import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskType;

public record NextOperationTaskResponse(boolean hasTask, Long taskId, OperationTaskType taskType,
        String parametersJson) {
    public static NextOperationTaskResponse from(OperationTask task) {
        return new NextOperationTaskResponse(true, task.getId(), task.getTaskType(),
                task.getParametersJson());
    }

    public static NextOperationTaskResponse empty() {
        return new NextOperationTaskResponse(false, null, null, null);
    }
}
