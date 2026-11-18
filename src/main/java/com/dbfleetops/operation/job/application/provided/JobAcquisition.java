package com.dbfleetops.operation.job.application.provided;

import com.dbfleetops.operation.job.dto.JobAcquisitionResponse;

/** Worker가 실행할 대기 Job 하나를 선점할 때 사용하는 입구입니다. */
public interface JobAcquisition {

    /** 실행 가능한 Job 하나를 선점해 실행권을 설정하고 실행을 시작합니다. */
    JobAcquisitionResponse acquireJob(String workerId);
}
