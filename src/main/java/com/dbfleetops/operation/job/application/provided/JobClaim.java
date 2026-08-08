package com.dbfleetops.operation.job.application.provided;

import com.dbfleetops.operation.job.dto.ClaimJobResponse;

/** Worker가 실행할 대기 Job을 가져갈 때 사용하는 입구입니다. */
public interface JobClaim {

    /** 실행 가능한 Job 하나를 가져와 실행권을 설정하고 실행을 시작합니다. */
    ClaimJobResponse claimJob(String workerId);
}
