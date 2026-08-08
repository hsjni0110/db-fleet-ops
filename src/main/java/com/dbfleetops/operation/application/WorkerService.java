package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.BackupTasks;
import com.dbfleetops.operation.application.provided.WorkerJobs;
import com.dbfleetops.operation.application.required.AuditWriter;
import com.dbfleetops.operation.application.required.ConfigurationApplyOutcome;
import com.dbfleetops.operation.application.required.ConfigurationCheckOutcome;
import com.dbfleetops.operation.application.required.ConfigurationJobRunner;
import com.dbfleetops.operation.application.required.JobStore;
import com.dbfleetops.operation.application.required.WorkerState;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.dto.FailJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.dto.SucceedJobRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

@Service
public class WorkerService implements WorkerJobs {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);
    private static final int CLAIM_CANDIDATE_LIMIT = 10;

    private final JobStore jobs;
    private final AuditWriter audit;
    private final BackupTasks backupTasks;
    private final ConfigurationJobRunner configurations;
    private final WorkerState workerState;
    private final Clock clock;
    private final OperationJobLeaseProperties lease;
    private final WorkerResultMessageFactory messages;

    public WorkerService(JobStore jobs, AuditWriter audit, BackupTasks backupTasks,
            ConfigurationJobRunner configurations, WorkerState workerState, Clock clock,
            OperationJobLeaseProperties lease, WorkerResultMessageFactory messages) {
        this.jobs = jobs;
        this.audit = audit;
        this.backupTasks = backupTasks;
        this.configurations = configurations;
        this.workerState = workerState;
        this.clock = clock;
        this.lease = lease;
        this.messages = messages;
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
        executeJob(workerId, job);

        return ClaimJobResponse.claimed(job);
    }

    @Override
    @Transactional
    public OperationJobResponse succeedJob(String workerId, Long jobId,
            SucceedJobRequest request) {
        validateSuccessReport(workerId, jobId, request);

        OperationJob job = requireOwnedRunningJob(workerId, jobId);

        completeJob(workerId, job, request.resultMessage());

        return OperationJobResponse.from(job);
    }

    @Override
    @Transactional
    public OperationJobResponse failJob(String workerId, Long jobId, FailJobRequest request) {
        validateFailureReport(workerId, jobId, request);

        OperationJob job = requireOwnedRunningJob(workerId, jobId);

        failJob(workerId, job, request.resultCode(), request.resultMessage(), request.retryable());

        return OperationJobResponse.from(job);
    }

    private Optional<OperationJob> findWaitingJob() {
        return jobs.findClaimable(JobStatus.QUEUED, now(), CLAIM_CANDIDATE_LIMIT)
                .stream()
                .findFirst();
    }

    private boolean cannotClaim(String workerId, OperationJob job) {
        if (!workerState.isShuttingDown()) {
            return false;
        }

        recordSkippedClaim(workerId, job);
        return true;
    }

    private void recordSkippedClaim(String workerId, OperationJob job) {
        String jobId = job == null ? "-" : String.valueOf(job.getId());
        String message = job == null
                ? "Job claim skipped because worker is shutting down."
                : "Job claim skipped after lookup because worker is shutting down.";

        log.info("job_claim_skipped reason=worker_shutdown workerId={} jobId={}", workerId, jobId);
        audit.record(workerId, "JOB_CLAIM_SKIPPED", "OPERATION_JOB", jobId, "SKIPPED", message);
    }

    private void startJob(String workerId, OperationJob job) {
        job.start(workerId, now().plus(lease.duration()));

        audit.record(workerId, "JOB_CLAIMED", "OPERATION_JOB",
                String.valueOf(job.getId()), "SUCCESS",
                "Job claimed by worker. leaseUntil=" + job.getLeaseUntil());
    }

    private void executeJob(String workerId, OperationJob job) {
        switch (job.getJobType()) {
            case BACKUP -> createBackupTask(workerId, job);
            case CONFIGURATION_CHECK -> checkConfiguration(workerId, job);
            case CONFIGURATION_APPLY -> applyConfiguration(workerId, job);
        }
    }

    private void createBackupTask(String workerId, OperationJob job) {
        backupTasks.createBackupTask(job.getId(), job.getTargetDatabaseId());

        audit.record(workerId, "OPERATION_TASK_CREATED", "OPERATION_JOB",
                String.valueOf(job.getId()), "SUCCESS", "Backup operation task created.");
    }

    private void checkConfiguration(String workerId, OperationJob job) {
        try {
            ConfigurationCheckOutcome outcome = configurations.check(job);
            completeConfigurationCheck(workerId, job, outcome);
        } catch (Exception exception) {
            failConfigurationCheck(workerId, job, exception);
        }
    }

    private void completeConfigurationCheck(String workerId, OperationJob job,
            ConfigurationCheckOutcome outcome) {
        String resultMessage = messages.configurationCheck(outcome);

        job.succeed(resultMessage);
        audit.record(workerId, "CONFIGURATION_CHECK_COMPLETED", "OPERATION_JOB",
                String.valueOf(job.getId()), "SUCCESS", resultMessage);
    }

    private void failConfigurationCheck(String workerId, OperationJob job, Exception exception) {
        failJob(workerId, job, exception.getClass().getSimpleName(),
                messages.failure(exception, "Configuration check failed."), true);
    }

    private void applyConfiguration(String workerId, OperationJob job) {
        try {
            ConfigurationApplyOutcome outcome = configurations.apply(job);
            completeConfigurationApply(workerId, job, outcome);
        } catch (Exception exception) {
            failConfigurationApply(workerId, job, exception);
        }
    }

    private void completeConfigurationApply(String workerId, OperationJob job,
            ConfigurationApplyOutcome outcome) {
        String resultMessage = messages.configurationApply(outcome);

        if (!outcome.succeeded()) {
            failJob(workerId, job, "CONFIGURATION_APPLY_FAILED", resultMessage, false);
            return;
        }

        job.succeed(resultMessage);
        audit.record(workerId, "CONFIGURATION_APPLY_COMPLETED", "OPERATION_JOB",
                String.valueOf(job.getId()), "SUCCESS", resultMessage);
    }

    private void failConfigurationApply(String workerId, OperationJob job, Exception exception) {
        failJob(workerId, job, exception.getClass().getSimpleName(),
                messages.failure(exception, "Configuration apply failed."), false);
    }

    private void completeJob(String workerId, OperationJob job, String resultMessage) {
        job.succeed(resultMessage);

        audit.record(workerId, "JOB_SUCCEEDED", "OPERATION_JOB",
                String.valueOf(job.getId()), "SUCCESS", resultMessage);
    }

    private void failJob(String workerId, OperationJob job, String resultCode,
            String resultMessage, boolean retryable) {
        job.fail(resultCode, resultMessage);

        audit.record(workerId, "JOB_FAILED", "OPERATION_JOB",
                String.valueOf(job.getId()), "FAILED", resultMessage);

        retryJob(workerId, job, retryable);
    }

    private void retryJob(String workerId, OperationJob job, boolean retryable) {
        if (!retryable || job.getRetryCount() >= job.getMaxRetryCount()) {
            return;
        }

        job.retry(now().plus(lease.retryDelay()));

        audit.record(workerId, "JOB_RETRIED", "OPERATION_JOB",
                String.valueOf(job.getId()), "SUCCESS",
                "Job re-queued. retryCount=" + job.getRetryCount());
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

    private void validateSuccessReport(String workerId, Long jobId, SucceedJobRequest request) {
        validateReportTarget(workerId, jobId);
        notNull(request, "Job 성공 결과는 필수입니다.");
    }

    private void validateFailureReport(String workerId, Long jobId, FailJobRequest request) {
        validateReportTarget(workerId, jobId);
        notNull(request, "Job 실패 결과는 필수입니다.");
        hasText(request.resultCode(), "실패 코드는 필수입니다.");
    }

    private void validateReportTarget(String workerId, Long jobId) {
        hasText(workerId, "Worker ID는 필수입니다.");
        notNull(jobId, "Job ID는 필수입니다.");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
