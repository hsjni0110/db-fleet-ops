package com.dbfleetops.operation.job.adapter.persistence;

import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface JpaOperationJobRepository extends JpaRepository<OperationJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from OperationJob job where job.id = :id")
    Optional<OperationJob> findByIdForUpdate(@Param("id") Long id);
    Optional<OperationJob> findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(
            Long databaseId, JobType type, String idempotencyKey);
    List<OperationJob> findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
            JobStatus status, LocalDateTime now);
    List<OperationJob> findAllByOrderByCreatedAtDesc();
    long countByStatus(JobStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from OperationJob job where job.status = :status and job.leaseUntil <= :now order by job.leaseUntil asc")
    List<OperationJob> findExpiredForUpdate(@Param("status") JobStatus status,
            @Param("now") LocalDateTime now, Pageable pageable);
}
