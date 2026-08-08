package com.dbfleetops.operation.infra;

import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

import java.util.List;

public interface OperationTaskRepository extends JpaRepository<OperationTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OperationTask> findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(Long agentId,
            OperationTaskStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OperationTask> findById(Long taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from OperationTask task where task.status = :status "
            + "and task.leaseExpiresAt <= :now order by task.leaseExpiresAt asc")
    List<OperationTask> findExpiredForUpdate(@Param("status") OperationTaskStatus status,
            @Param("now") LocalDateTime now, Pageable pageable);

    List<OperationTask> findTop10ByAgentIdOrderByCreatedAtDesc(Long agentId);

    long countByStatus(OperationTaskStatus status);

    List<OperationTask> findByOperationJobIdOrderByCreatedAtAsc(Long operationJobId);
}
