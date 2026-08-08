package com.dbfleetops.operation.task.adapter.persistence;

import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskStatus;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class TaskStoreAdapter implements TaskStore {
    private final JpaOperationTaskRepository repository;
    public TaskStoreAdapter(JpaOperationTaskRepository repository) { this.repository = repository; }
    public OperationTask save(OperationTask task) { return repository.save(task); }
    public Optional<OperationTask> findById(Long id) { return repository.findById(id); }
    public Optional<OperationTask> findNextForUpdate(Long agentId, OperationTaskStatus status) {
        return repository.findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(agentId, status);
    }
    public List<OperationTask> findExpiredForUpdate(OperationTaskStatus status,
            LocalDateTime now, int limit) {
        return repository.findExpiredForUpdate(status, now, PageRequest.of(0, limit));
    }
    public List<OperationTask> findRecentByAgent(Long agentId) {
        return repository.findTop10ByAgentIdOrderByCreatedAtDesc(agentId);
    }
    public List<OperationTask> findByJob(Long jobId) {
        return repository.findByOperationJobIdOrderByCreatedAtAsc(jobId);
    }
    public boolean existsByJobAndType(Long jobId, OperationTaskType type) {
        return repository.existsByOperationJobIdAndTaskType(jobId, type);
    }
    public long countByStatus(OperationTaskStatus status) { return repository.countByStatus(status); }
}
