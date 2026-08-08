package com.dbfleetops.operation.job.application.service;

import com.dbfleetops.operation.job.application.execution.JobExecutionDispatcher;
import com.dbfleetops.operation.job.application.service.OperationJobLeaseProperties;
import com.dbfleetops.operation.job.application.provided.JobClaim;
import com.dbfleetops.operation.shared.application.required.AuditWriter;
import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.application.required.WorkerState;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.job.dto.ClaimJobResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.util.Assert.hasText;

@Service
public class JobClaimService implements JobClaim {

    private static final Logger log = LoggerFactory.getLogger(JobClaimService.class);
    private static final int CLAIM_CANDIDATE_LIMIT = 10;

    private final JobStore jobs;
    private final WorkerState workerState;
    private final JobExecutionDispatcher executions;
    private final JobExecutionResultService results;
    private final AuditWriter audit;
    private final Clock clock;
    private final OperationJobLeaseProperties lease;

    public JobClaimService(JobStore jobs, WorkerState workerState,
            JobExecutionDispatcher executions, JobExecutionResultService results,
            AuditWriter audit, Clock clock, OperationJobLeaseProperties lease) {
        this.jobs = jobs;
        this.workerState = workerState;
        this.executions = executions;
        this.results = results;
        this.audit = audit;
        this.clock = clock;
        this.lease = lease;
    }

    @Override
    @Transactional
    public ClaimJobResponse claimJob(String workerId) {
        hasText(workerId, "Worker ID는 필수입니다.");

        if (cannotClaim(workerId, null)) {
            return ClaimJobResponse.empty();
        }

        Optional<OperationJob> waitingJob = findWaitingJob();
        if (waitingJob.isEmpty()) {
            return ClaimJobResponse.empty();
        }

        OperationJob job = waitingJob.get();
        if (cannotClaim(workerId, job)) {
            return ClaimJobResponse.empty();
        }

        startJob(workerId, job);
        results.apply(workerId, job, executions.execute(job));

        return ClaimJobResponse.claimed(job);
    }

    private Optional<OperationJob> findWaitingJob() {
        return jobs.findClaimable(JobStatus.QUEUED, now(), CLAIM_CANDIDATE_LIMIT)
                .stream().findFirst();
    }

    private void startJob(String workerId, OperationJob job) {
        LocalDateTime startedAt = now();
        job.start(workerId, startedAt, startedAt.plus(lease.duration()));
        audit.record(workerId, "JOB_CLAIMED", "OPERATION_JOB", String.valueOf(job.getId()),
                "SUCCESS", "Job claimed by worker. leaseUntil=" + job.getLeaseUntil());
    }

    private boolean cannotClaim(String workerId, OperationJob job) {
        if (!workerState.isShuttingDown()) {
            return false;
        }

        String jobId = job == null ? "-" : String.valueOf(job.getId());
        String message = job == null
                ? "Job claim skipped because worker is shutting down."
                : "Job claim skipped after lookup because worker is shutting down.";
        log.info("job_claim_skipped reason=worker_shutdown workerId={} jobId={}", workerId, jobId);
        audit.record(workerId, "JOB_CLAIM_SKIPPED", "OPERATION_JOB", jobId, "SKIPPED", message);
        return true;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
