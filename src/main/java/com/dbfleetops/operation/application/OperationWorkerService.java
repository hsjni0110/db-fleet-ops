package com.dbfleetops.operation.application;

import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.infra.OperationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
}