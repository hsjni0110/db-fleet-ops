package com.dbfleetops.operation.job.dto;

import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;

import java.time.LocalDateTime;

public record JobAcquisitionResponse(
        boolean acquired,
        Long jobId,
        JobType jobType,
        JobStatus status,
        Long targetDatabaseId,
        String leaseOwner,
        LocalDateTime leaseUntil
) {
    public static JobAcquisitionResponse acquired(
            OperationJob job
    ) {
        return new JobAcquisitionResponse(
                true,
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getTargetDatabaseId(),
                job.getLeaseOwner(),
                job.getLeaseUntil()
        );
    }

    public static JobAcquisitionResponse empty() {
        return new JobAcquisitionResponse(
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
