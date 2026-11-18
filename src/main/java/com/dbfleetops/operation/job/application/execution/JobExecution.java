package com.dbfleetops.operation.job.application.execution;

import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;

/** 선점한 Job 한 종류의 실행을 시작합니다. */
public interface JobExecution {

    boolean supports(JobType jobType);

    JobExecutionOutcome execute(OperationJob job);
}
