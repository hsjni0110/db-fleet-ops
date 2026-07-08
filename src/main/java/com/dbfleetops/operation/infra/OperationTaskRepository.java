package com.dbfleetops.operation.infra;

import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationTaskRepository extends JpaRepository<OperationTask, Long> {

    List<OperationTask> findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(Long agentId,
            OperationTaskStatus status);

    long countByStatus(OperationTaskStatus status);
}
