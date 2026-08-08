package com.dbfleetops.operation.task.application.provided;

import com.dbfleetops.operation.task.dto.NextOperationTaskResponse;

/** Agent가 다음 실행할 Task를 요청할 때 사용하는 입구입니다. */
public interface TaskClaim {
    /** Agent Token을 확인하고 가장 오래 기다린 Task 하나를 선점해 실행 정보와 함께 반환합니다. */
    NextOperationTaskResponse claimNext(Long agentId, String agentToken);
}
