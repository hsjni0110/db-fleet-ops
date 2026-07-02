package com.dbfleetops.operation.application;

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
import java.util.Objects;

@Service
public class OperationWorkerService {

    private static final long LEASE_SECONDS = 60L;

    private final OperationJobRepository jobRepository;

    public OperationWorkerService(
            OperationJobRepository jobRepository
    ) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public ClaimJobResponse claimJob(
            String workerId
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        List<OperationJob> candidates =
                jobRepository
                        .findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                                JobStatus.QUEUED,
                                now
                        );

        if (candidates.isEmpty()) {
            return ClaimJobResponse.empty();
        }

        OperationJob job =
                candidates.getFirst();

        job.start(
                workerId,
                now.plusSeconds(LEASE_SECONDS)
        );

        return ClaimJobResponse.claimed(job);
    }

    @Transactional
    public OperationJobResponse succeedJob(
            String workerId,
            Long jobId,
            SucceedJobRequest request
    ) {
        OperationJob job =
                getRunningJobOwnedByWorker(
                        workerId,
                        jobId
                );

        job.succeed(
                request.resultMessage()
        );

        return OperationJobResponse.from(job);
    }

    @Transactional
    public OperationJobResponse failJob(
            String workerId,
            Long jobId,
            FailJobRequest request
    ) {
        OperationJob job =
                getRunningJobOwnedByWorker(
                        workerId,
                        jobId
                );

        job.fail(
                request.resultCode(),
                request.resultMessage()
        );

        if (request.retryable() && job.getRetryCount() < job.getMaxRetryCount()) {
            job.retry(
                    LocalDateTime.now().plusSeconds(30)
            );
        }

        return OperationJobResponse.from(job);
    }

    private OperationJob getRunningJobOwnedByWorker(
            String workerId,
            Long jobId
    ) {
        OperationJob job =
                jobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Operation job not found. jobId=" + jobId
                        ));

        if (job.getStatus() != JobStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING job can be completed. currentStatus="
                            + job.getStatus()
            );
        }

        if (!Objects.equals(job.getLeaseOwner(), workerId)) {
            throw new IllegalStateException(
                    "Worker does not own this job. workerId="
                            + workerId
                            + ", leaseOwner="
                            + job.getLeaseOwner()
            );
        }

        return job;
    }
}

