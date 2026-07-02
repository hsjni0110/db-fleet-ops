package com.dbfleetops.operation.infra;

import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OperationJobRepository extends JpaRepository<OperationJob, Long> {

    Optional<OperationJob> findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(
            Long targetDatabaseId,
            JobType jobType,
            String idempotencyKey
    );

    List<OperationJob> findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
            JobStatus status,
            LocalDateTime now
    );
}