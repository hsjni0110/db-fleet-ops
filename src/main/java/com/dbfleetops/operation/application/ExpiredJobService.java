package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.ExpiredJobs;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ExpiredJobService implements ExpiredJobs {
    private final JobStore jobRepository;
    private final TaskStore taskRepository;
    private final OperationJobLeaseProperties properties;
    private final AuditWriter audit;
    private final Clock clock;
    private final JobTaskCoordinator coordinator;

    public ExpiredJobService(JobStore jobRepository,
            TaskStore taskRepository, OperationJobLeaseProperties properties,
            AuditWriter audit, Clock clock, JobTaskCoordinator coordinator) {
        this.jobRepository = jobRepository;
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
        this.coordinator = coordinator;
    }

    @Transactional
    public int recoverExpiredJobs() {
        LocalDateTime now = LocalDateTime.now(clock);
        var jobs = jobRepository.findExpiredForUpdate(JobStatus.RUNNING, now,
                properties.expirationBatchSize());
        for (var job : jobs) reconcile(job, now);
        return jobs.size();
    }

    private void reconcile(com.dbfleetops.operation.domain.OperationJob job, LocalDateTime now) {
        var tasks = taskRepository.findByJob(job.getId());
        boolean active = tasks.stream().anyMatch(task -> task.getStatus() == OperationTaskStatus.QUEUED
                || task.getStatus() == OperationTaskStatus.RUNNING);
        if (active) {
            job.extendLease(now, now.plus(properties.duration()));
            record(job.getId(), "JOB_LEASE_EXTENDED", "Active Task still exists.");
            return;
        }
        if (tasks.isEmpty()) {
            if (job.getRetryCount() < job.getMaxRetryCount()) {
                job.requeueExpiredLease(now, now.plus(properties.retryDelay()));
                record(job.getId(), "JOB_REQUEUED", "Expired Job has no Task.");
            } else {
                job.timeout("JOB_LEASE_EXPIRED", "Job lease expired after maximum retries.");
                record(job.getId(), "JOB_TIMED_OUT", "Maximum retries reached.");
            }
            return;
        }
        coordinator.reconcile(job);
        record(job.getId(), "JOB_RECONCILED", "Job state reconciled from linked Tasks.");
    }

    private void record(Long jobId, String action, String message) {
        audit.record("job-lease-reaper", action, "OPERATION_JOB", String.valueOf(jobId),
                "SUCCESS", message);
    }
}
