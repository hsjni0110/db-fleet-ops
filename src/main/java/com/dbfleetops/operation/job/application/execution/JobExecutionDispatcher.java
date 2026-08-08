package com.dbfleetops.operation.job.application.execution;

import com.dbfleetops.operation.job.domain.OperationJob;
import org.springframework.stereotype.Component;

import java.util.List;

/** Job 종류에 맞는 실행 객체를 찾아 실행을 시작합니다. */
@Component
public class JobExecutionDispatcher {

    private final List<JobExecution> executions;

    public JobExecutionDispatcher(List<JobExecution> executions) {
        this.executions = executions;
    }

    public JobExecutionOutcome execute(OperationJob job) {
        JobExecution execution = executions.stream()
                .filter(candidate -> candidate.supports(job.getJobType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Job 실행 방법을 찾을 수 없습니다. jobType=" + job.getJobType()));

        return execution.execute(job);
    }
}
