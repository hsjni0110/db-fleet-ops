package com.dbfleetops.operation.task.application.provided;

/** Task Lease 만료를 점검하는 Scheduler가 사용하는 입구입니다. */
public interface ExpiredTasks {
    /** Lease가 만료된 Task를 찾아 재대기하거나 시간 초과로 끝내고, 처리한 Task 수를 반환합니다. */
    int recoverExpiredTasks();
}
