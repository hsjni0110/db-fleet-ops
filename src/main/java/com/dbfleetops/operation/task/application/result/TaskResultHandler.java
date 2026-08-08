package com.dbfleetops.operation.task.application.result;

import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;

/** Task 종류에 맞게 성공 결과의 후속 처리를 수행합니다. */
public interface TaskResultHandler {
    boolean supports(OperationTaskType taskType);
    void handle(OperationTask task, String resultPayloadJson);
}
