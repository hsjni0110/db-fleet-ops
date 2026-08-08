package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.WorkerJobs;
import com.dbfleetops.operation.application.provided.BackupTasks;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.dto.FailJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.dto.SucceedJobRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkerService implements WorkerJobs {

        private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

        private final JobStore jobRepository;
        private final AuditWriter auditRecorderPort;
        private final BackupTasks backupTasks;
        private final ConfigurationJobRunner configurationJobs;
        private final WorkerState workerShutdownState;
        private final Clock clock;
        private final OperationJobLeaseProperties leaseProperties;

        @Autowired
        public WorkerService(JobStore jobRepository,
                        AuditWriter auditRecorderPort,
                        BackupTasks backupTasks,
                        ConfigurationJobRunner configurationJobs,
                        WorkerState workerShutdownState, Clock clock,
                        OperationJobLeaseProperties leaseProperties) {
                this.jobRepository = jobRepository;
                this.auditRecorderPort = auditRecorderPort;
                this.backupTasks = backupTasks;
                this.configurationJobs = configurationJobs;
                this.workerShutdownState = workerShutdownState;
                this.clock = clock;
                this.leaseProperties = leaseProperties;
        }

        WorkerService(JobStore jobRepository,
                        AuditWriter auditRecorderPort,
                        BackupTasks backupTasks,
                        ConfigurationJobRunner configurationJobs,
                        WorkerState workerShutdownState) {
                this(jobRepository, auditRecorderPort, backupTasks,
                                configurationJobs,
                                workerShutdownState, Clock.systemUTC(),
                                new OperationJobLeaseProperties(null, null, null, 100, true));
        }

        @Transactional
        public ClaimJobResponse claimJob(String workerId) {
                if (workerShutdownState.isShuttingDown()) {
                        log.info("job_claim_skipped reason=worker_shutdown workerId={}", workerId);

                        auditRecorderPort.record(workerId, "JOB_CLAIM_SKIPPED", "OPERATION_JOB",
                                        "-", "SKIPPED",
                                        "Job claim skipped because worker is shutting down.");

                        return ClaimJobResponse.empty();
                }

                List<OperationJob> jobs = jobRepository
                                .findClaimable(JobStatus.QUEUED, LocalDateTime.now(clock), 10);

                if (jobs.isEmpty()) {
                        return ClaimJobResponse.empty();
                }

                OperationJob job = jobs.getFirst();

                if (workerShutdownState.isShuttingDown()) {
                        log.info("job_claim_skipped_after_lookup reason=worker_shutdown workerId={} jobId={}",
                                        workerId, job.getId());

                        auditRecorderPort.record(workerId, "JOB_CLAIM_SKIPPED", "OPERATION_JOB",
                                        String.valueOf(job.getId()), "SKIPPED",
                                        "Job claim skipped after lookup because worker is shutting down.");

                        return ClaimJobResponse.empty();
                }

                LocalDateTime now = LocalDateTime.now(clock);
                job.start(workerId, now.plus(leaseProperties.duration()));

                auditRecorderPort.record(workerId, "JOB_CLAIMED", "OPERATION_JOB",
                                String.valueOf(job.getId()), "SUCCESS",
                                "Job claimed by worker. leaseUntil=" + job.getLeaseUntil());

                if (job.getJobType() == JobType.BACKUP) {
                        createBackupOperationTask(workerId, job);
                }

                if (job.getJobType() == JobType.CONFIGURATION_CHECK) {
                        executeConfigurationCheckJob(workerId, job);
                }

                if (job.getJobType() == JobType.CONFIGURATION_APPLY) {
                        executeConfigurationApplyJob(workerId, job);
                }

                return ClaimJobResponse.claimed(job);
        }

        @Transactional
        public OperationJobResponse succeedJob(String workerId, Long jobId,
                        SucceedJobRequest request) {
                OperationJob job = getRunningJobOwnedByWorker(workerId, jobId);

                job.succeed(request.resultMessage());

                auditRecorderPort.record(workerId, "JOB_SUCCEEDED", "OPERATION_JOB",
                                String.valueOf(job.getId()), "SUCCESS", request.resultMessage());

                return OperationJobResponse.from(job);
        }

        @Transactional
        public OperationJobResponse failJob(String workerId, Long jobId, FailJobRequest request) {
                OperationJob job = getRunningJobOwnedByWorker(workerId, jobId);

                failAndRetryIfNeeded(workerId, job, request.resultCode(), request.resultMessage(),
                                request.retryable());

                return OperationJobResponse.from(job);
        }

        private void createBackupOperationTask(String workerId, OperationJob job) {
                backupTasks.createBackupTask(job.getId(),
                                job.getTargetDatabaseId());

                auditRecorderPort.record(workerId, "OPERATION_TASK_CREATED", "OPERATION_JOB",
                                String.valueOf(job.getId()), "SUCCESS",
                                "Backup operation task created.");
        }

        private void executeConfigurationCheckJob(String workerId, OperationJob job) {
                try {
                        ConfigurationCheckOutcome driftResponse = configurationJobs.check(job);

                        String resultMessage = "Configuration check completed. driftId="
                                        + driftResponse.driftId() + ", status="
                                        + driftResponse.status();

                        job.succeed(resultMessage);

                        auditRecorderPort.record(workerId, "CONFIGURATION_CHECK_COMPLETED",
                                        "OPERATION_JOB", String.valueOf(job.getId()), "SUCCESS",
                                        resultMessage);
                } catch (Exception exception) {
                        String resultCode = exception.getClass().getSimpleName();

                        String resultMessage =
                                        exception.getMessage() != null ? exception.getMessage()
                                                        : "Configuration check failed.";

                        failAndRetryIfNeeded(workerId, job, resultCode, resultMessage, true);
                }
        }

        private void executeConfigurationApplyJob(String workerId, OperationJob job) {
                try {
                        ConfigurationApplyOutcome apply = configurationJobs.apply(job);

                        String resultMessage = "Configuration apply completed. applyId="
                                        + apply.applyId() + ", status=" + apply.status()
                                        + ", successCount=" + apply.successCount()
                                        + ", failedCount=" + apply.failedCount()
                                        + ", skippedCount=" + apply.skippedCount();

                        if (apply.succeeded()) {
                                job.succeed(resultMessage);

                                auditRecorderPort.record(workerId, "CONFIGURATION_APPLY_COMPLETED",
                                                "OPERATION_JOB", String.valueOf(job.getId()),
                                                "SUCCESS", resultMessage);

                                return;
                        }

                        failAndRetryIfNeeded(workerId, job, "CONFIGURATION_APPLY_FAILED",
                                        resultMessage, false);
                } catch (Exception exception) {
                        String resultCode = exception.getClass().getSimpleName();

                        String resultMessage =
                                        exception.getMessage() != null ? exception.getMessage()
                                                        : "Configuration apply failed.";

                        failAndRetryIfNeeded(workerId, job, resultCode, resultMessage, false);
                }
        }

        private void failAndRetryIfNeeded(String workerId, OperationJob job, String resultCode,
                        String resultMessage, boolean retryable) {
                job.fail(resultCode, resultMessage);

                auditRecorderPort.record(workerId, "JOB_FAILED", "OPERATION_JOB",
                                String.valueOf(job.getId()), "FAILED", resultMessage);

                if (retryable && job.getRetryCount() < job.getMaxRetryCount()) {
                        job.retry(LocalDateTime.now(clock).plus(leaseProperties.retryDelay()));

                        auditRecorderPort.record(workerId, "JOB_RETRIED", "OPERATION_JOB",
                                        String.valueOf(job.getId()), "SUCCESS",
                                        "Job re-queued. retryCount=" + job.getRetryCount());
                }
        }

        private OperationJob getRunningJobOwnedByWorker(String workerId, Long jobId) {
                OperationJob job = jobRepository.findById(jobId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Operation job not found. jobId=" + jobId));

                if (job.getStatus() != JobStatus.RUNNING) {
                        throw new IllegalStateException("Job is not running. jobId=" + jobId
                                        + ", status=" + job.getStatus());
                }

                if (!workerId.equals(job.getLeaseOwner())) {
                        throw new IllegalStateException("Job is not owned by worker. jobId=" + jobId
                                        + ", workerId=" + workerId + ", leaseOwner="
                                        + job.getLeaseOwner());
                }

                return job;
        }
}
