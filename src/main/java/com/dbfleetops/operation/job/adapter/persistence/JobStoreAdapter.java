package com.dbfleetops.operation.job.adapter.persistence;

import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class JobStoreAdapter implements JobStore {
    private final JpaOperationJobRepository repository;
    public JobStoreAdapter(JpaOperationJobRepository repository) { this.repository = repository; }
    public OperationJob save(OperationJob job) { return repository.save(job); }
    public Optional<OperationJob> findById(Long id) { return repository.findById(id); }
    public Optional<OperationJob> findByIdForUpdate(Long id) { return repository.findByIdForUpdate(id); }
    public Optional<OperationJob> findDuplicate(Long databaseId, JobType type, String key) {
        return repository.findByTargetDatabaseIdAndJobTypeAndIdempotencyKey(databaseId, type, key);
    }
    public List<OperationJob> findClaimable(JobStatus status, LocalDateTime now, int limit) {
        return repository.findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(status, now)
                .stream().limit(limit).toList();
    }
    public List<OperationJob> findLatest() { return repository.findAllByOrderByCreatedAtDesc(); }
    public List<OperationJob> findExpiredForUpdate(JobStatus status, LocalDateTime now, int limit) {
        return repository.findExpiredForUpdate(status, now, PageRequest.of(0, limit));
    }
    public long countByStatus(JobStatus status) { return repository.countByStatus(status); }
}
