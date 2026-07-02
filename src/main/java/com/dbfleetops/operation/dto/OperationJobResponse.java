package com.dbfleetops.operation.dto;

import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;

import java.time.LocalDateTime;

public record OperationJobResponse(
        Long jobId,
        JobType jobType,
        JobStatus status,
        Long targetDatabaseId,
        String requestedBy,
        int retryCount,
        int maxRetryCount,
        String leaseOwner,
        LocalDateTime leaseUntil,
        LocalDateTime availableAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String resultCode,
        String resultMessage,
        LocalDateTime createdAt
) {
    public static OperationJobResponse from(
            OperationJob job
    ) {
        return new OperationJobResponse(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getTargetDatabaseId(),
                job.getRequestedBy(),
                job.getRetryCount(),
                job.getMaxRetryCount(),
                job.getLeaseOwner(),
                job.getLeaseUntil(),
                job.getAvailableAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getResultCode(),
                job.getResultMessage(),
                job.getCreatedAt()
        );
    }
}