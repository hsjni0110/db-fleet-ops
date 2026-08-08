package com.dbfleetops.operation.job.application.provided;

import com.dbfleetops.operation.job.dto.FailJobRequest;
import com.dbfleetops.operation.job.dto.OperationJobResponse;
import com.dbfleetops.operation.job.dto.SucceedJobRequest;

/** Worker가 직접 실행한 Job 결과를 보고할 때 사용하는 입구입니다. */
public interface JobReports {

    /** Job 실행권을 확인하고 성공 결과를 반영합니다. */
    OperationJobResponse succeedJob(String workerId, Long jobId, SucceedJobRequest request);

    /** Job 실행권을 확인하고 실패 결과와 재시도 여부를 반영합니다. */
    OperationJobResponse failJob(String workerId, Long jobId, FailJobRequest request);
}
