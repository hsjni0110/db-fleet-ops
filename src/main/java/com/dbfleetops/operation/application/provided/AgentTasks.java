package com.dbfleetops.operation.application.provided;

import com.dbfleetops.operation.dto.*;

/**
 * Agent와 관련된 Task 요청을 받는 입구입니다.
 * HTTP Controller는 구체적인 Service 대신 이 인터페이스를 사용합니다.
 */
public interface AgentTasks {
    /** 관리 또는 테스트 목적으로 새로운 Task를 대기 상태로 만듭니다. */
    OperationTaskResponse createTask(CreateOperationTaskRequest request);

}
