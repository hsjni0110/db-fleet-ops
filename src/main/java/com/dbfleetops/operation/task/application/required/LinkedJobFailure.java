package com.dbfleetops.operation.task.application.required;

/**
 * Task가 자신의 실패나 최종 시간 초과를 연결된 Job에 전달할 때 사용하는 출구입니다.
 * Task 영역은 Job의 저장 방식과 상태 변경 구현을 알지 않습니다.
 */
public interface LinkedJobFailure {

    /** 실패한 Task의 오류를 연결된 Job에 반영합니다. */
    void fail(Long jobId, String code, String message);

    /** 최종 시간 초과된 Task의 오류를 연결된 Job에 반영합니다. */
    void timeout(Long jobId, String code, String message);
}
