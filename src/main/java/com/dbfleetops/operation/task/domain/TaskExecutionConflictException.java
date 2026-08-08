package com.dbfleetops.operation.task.domain;

/**
 * 현재 Task의 상태, 실행 번호 또는 Lease로 요청한 작업을 수행할 수 없을 때 발생합니다.
 *
 * <p>이 예외는 HTTP 상태를 알지 못하며, Task 실행 규칙의 충돌만 표현합니다.</p>
 */
public class TaskExecutionConflictException extends IllegalStateException {

    public TaskExecutionConflictException(String message) {
        super(message);
    }
}
