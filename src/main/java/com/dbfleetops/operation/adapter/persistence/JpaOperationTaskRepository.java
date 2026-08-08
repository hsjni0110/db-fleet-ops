package com.dbfleetops.operation.adapter.persistence;

import com.dbfleetops.operation.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface JpaOperationTaskRepository extends JpaRepository<OperationTask, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OperationTask> findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(Long agentId,
            OperationTaskStatus status);
    @Override @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OperationTask> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from OperationTask task where task.status = :status and task.leaseExpiresAt <= :now order by task.leaseExpiresAt asc")
    List<OperationTask> findExpiredForUpdate(@Param("status") OperationTaskStatus status,
            @Param("now") LocalDateTime now, Pageable pageable);
    List<OperationTask> findTop10ByAgentIdOrderByCreatedAtDesc(Long agentId);
    long countByStatus(OperationTaskStatus status);
    List<OperationTask> findByOperationJobIdOrderByCreatedAtAsc(Long operationJobId);
    boolean existsByOperationJobIdAndTaskType(Long operationJobId, OperationTaskType taskType);
}
