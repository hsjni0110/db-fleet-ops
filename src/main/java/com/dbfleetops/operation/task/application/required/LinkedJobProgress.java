package com.dbfleetops.operation.task.application.required;

/**
 * Task가 자신의 최종 결과를 연결된 Job에 전달할 때 사용하는 출구입니다.
 * Task 모듈은 Job의 저장 방식이나 상태 변경 구현을 직접 알지 않습니다.
 */
public interface LinkedJobProgress {

    /** 실패한 Task가 연결된 Job에 실패 사유를 전달합니다. */
    void fail(Long jobId, String code, String message);

    /** 최종 시간 초과된 Task가 연결된 Job에 시간 초과 사유를 전달합니다. */
    void timeout(Long jobId, String code, String message);
}
