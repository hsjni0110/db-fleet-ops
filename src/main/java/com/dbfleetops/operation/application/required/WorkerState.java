package com.dbfleetops.operation.application.required;

/** Operation이 Worker 종료 중에는 새 Job을 가져가지 않도록 확인할 때 사용하는 출구입니다. */
public interface WorkerState {
    /** Worker가 종료를 시작했으면 true를 반환합니다. */
    boolean isShuttingDown();
}
