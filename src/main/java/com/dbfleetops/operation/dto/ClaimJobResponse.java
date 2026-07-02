package com.dbfleetops.operation.dto;

import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;

import java.time.LocalDateTime;

public record ClaimJobResponse(
        boolean claimed,
        Long jobId,
        JobType jobType,
        JobStatus status,
        Long targetDatabaseId,
        String leaseOwner,
        LocalDateTime leaseUntil
) {
    public static ClaimJobResponse claimed(
            OperationJob job
    ) {
        return new ClaimJobResponse(
                true,
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getTargetDatabaseId(),
                job.getLeaseOwner(),
                job.getLeaseUntil()
        );
    }

    public static ClaimJobResponse empty() {
        return new ClaimJobResponse(
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}