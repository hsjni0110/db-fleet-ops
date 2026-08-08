package com.dbfleetops.operation.workflow.application.provided;

/** Job Lease 만료를 점검하는 Scheduler가 사용하는 입구입니다. */
public interface ExpiredJobs {
    /** Lease가 만료된 Job을 찾아 연결된 Task 상태에 맞게 정리하고, 처리한 Job 수를 반환합니다. */
    int recoverExpiredJobs();
}
