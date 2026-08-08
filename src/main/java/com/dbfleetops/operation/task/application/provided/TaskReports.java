package com.dbfleetops.operation.task.application.provided;

import com.dbfleetops.operation.task.dto.CompleteOperationTaskRequest;
import com.dbfleetops.operation.task.dto.FailOperationTaskRequest;
import com.dbfleetops.operation.task.dto.OperationTaskResponse;

/** Agent가 보낸 Task 실행 결과를 받는 입구입니다. */
public interface TaskReports {
    /** 성공 결과를 한 번만 반영하고, 같은 보고는 기존 결과로 응답합니다. */
    OperationTaskResponse completeTask(Long agentId, Long taskId, CompleteOperationTaskRequest request);
    /** 실패 결과를 한 번만 반영하고 연결된 Job에 실패를 전달합니다. */
    OperationTaskResponse failTask(Long agentId, Long taskId, FailOperationTaskRequest request);
}
