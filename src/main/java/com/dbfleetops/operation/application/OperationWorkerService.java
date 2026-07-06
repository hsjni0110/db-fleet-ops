package com.dbfleetops.operation.application;

import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.dto.FailJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.dto.SucceedJobRequest;
import com.dbfleetops.operation.infra.OperationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OperationWorkerService {

        private static final long LEASE_SECONDS = 60L;
        private static final long RETRY_DELAY_SECONDS = 30L;

        private final OperationJobRepository jobRepository;
        private final AuditRecorderPort auditRecorderPort;

        public OperationWorkerService(OperationJobRepository jobRepository,
                        AuditRecorderPort auditRecorderPort) {
                this.jobRepository = jobRepository;
                this.auditRecorderPort = auditRecorderPort;
        }

        @Transactional
        public ClaimJobResponse claimJob(String workerId) {
                List<OperationJob> jobs = jobRepository
                                .findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                                                JobStatus.QUEUED, LocalDateTime.now());

                if (jobs.isEmpty()) {
                        return ClaimJobResponse.empty();
                }

                OperationJob job = jobs.getFirst();

                job.start(workerId, LocalDateTime.now().plusSeconds(LEASE_SECONDS));

                auditRecorderPort.record(workerId, "JOB_CLAIMED", "OPERATION_JOB",
                                String.valueOf(job.getId()), "SUCCESS",
                                "Job claimed by worker. leaseUntil=" + job.getLeaseUntil());

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

                job.fail(request.resultCode(), request.resultMessage());

                auditRecorderPort.record(workerId, "JOB_FAILED", "OPERATION_JOB",
                                String.valueOf(job.getId()), "FAILED", request.resultMessage());

                if (request.retryable() && job.getRetryCount() < job.getMaxRetryCount()) {
                        job.retry(LocalDateTime.now().plusSeconds(RETRY_DELAY_SECONDS));

                        auditRecorderPort.record(workerId, "JOB_RETRIED", "OPERATION_JOB",
                                        String.valueOf(job.getId()), "SUCCESS",
                                        "Job re-queued. retryCount=" + job.getRetryCount());
                }

                return OperationJobResponse.from(job);
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
