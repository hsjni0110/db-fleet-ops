package com.dbfleetops.operation.infra;

import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OperationJobRepository extends JpaRepository<OperationJob, Long> {

        Optional<OperationJob> findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(
                        Long targetDatabaseId, JobType jobType, String idempotencyKey);

        List<OperationJob> findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                        JobStatus status, LocalDateTime now);

        List<OperationJob> findAllByOrderByCreatedAtDesc();

        long countByStatus(JobStatus status);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select job from OperationJob job where job.status = :status "
                        + "and job.leaseUntil <= :now order by job.leaseUntil asc")
        List<OperationJob> findExpiredForUpdate(@Param("status") JobStatus status,
                        @Param("now") LocalDateTime now, Pageable pageable);
}
