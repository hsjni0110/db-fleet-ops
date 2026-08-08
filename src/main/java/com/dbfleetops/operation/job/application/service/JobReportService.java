package com.dbfleetops.operation.job.application.service;

import com.dbfleetops.operation.job.application.provided.JobReports;
import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.job.dto.FailJobRequest;
import com.dbfleetops.operation.job.dto.OperationJobResponse;
import com.dbfleetops.operation.job.dto.SucceedJobRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

@Service
public class JobReportService implements JobReports {

    private final JobStore jobs;
    private final JobExecutionResultService results;

    public JobReportService(JobStore jobs, JobExecutionResultService results) {
        this.jobs = jobs;
        this.results = results;
    }

    @Override
    @Transactional
    public OperationJobResponse succeedJob(String workerId, Long jobId,
            SucceedJobRequest request) {
        validateTarget(workerId, jobId);
        notNull(request, "Job 성공 결과는 필수입니다.");

        OperationJob job = requireOwnedRunningJob(workerId, jobId);
        results.succeed(workerId, job, request.resultMessage());
        return OperationJobResponse.from(job);
    }

    @Override
    @Transactional
    public OperationJobResponse failJob(String workerId, Long jobId, FailJobRequest request) {
        validateTarget(workerId, jobId);
        notNull(request, "Job 실패 결과는 필수입니다.");
        hasText(request.resultCode(), "실패 코드는 필수입니다.");

        OperationJob job = requireOwnedRunningJob(workerId, jobId);
        results.fail(workerId, job, request.resultCode(), request.resultMessage(),
                request.retryable());
        return OperationJobResponse.from(job);
    }

    private OperationJob requireOwnedRunningJob(String workerId, Long jobId) {
        OperationJob job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Job을 찾을 수 없습니다. jobId=" + jobId));

        state(job.getStatus() == JobStatus.RUNNING,
                "실행 중인 Job만 결과를 보고할 수 있습니다. jobId=" + jobId
                        + ", status=" + job.getStatus());
        state(workerId.equals(job.getLeaseOwner()),
                "Job 실행권을 가진 Worker만 결과를 보고할 수 있습니다. jobId=" + jobId
                        + ", workerId=" + workerId + ", leaseOwner=" + job.getLeaseOwner());
        return job;
    }

    private void validateTarget(String workerId, Long jobId) {
        hasText(workerId, "Worker ID는 필수입니다.");
        notNull(jobId, "Job ID는 필수입니다.");
    }
}
