package com.dbfleetops.operation.application.provided;

import com.dbfleetops.operation.dto.*;

/**
 * Worker가 Job을 가져가고 실행 결과를 알릴 때 사용하는 입구입니다.
 * HTTP Controller와 Scheduler는 구체적인 Service 대신 이 인터페이스를 사용합니다.
 */
public interface WorkerJobs {
    /** Worker가 실행할 수 있는 대기 Job 하나를 선점합니다. */
    ClaimJobResponse claimJob(String workerId);

    /** Worker가 성공한 Job의 결과를 저장하고 Job을 성공 상태로 끝냅니다. */
    OperationJobResponse succeedJob(String workerId, Long jobId, SucceedJobRequest request);

    /** Worker가 실패한 Job의 오류를 저장하고 필요하면 재시도 상태로 돌려놓습니다. */
    OperationJobResponse failJob(String workerId, Long jobId, FailJobRequest request);
}
