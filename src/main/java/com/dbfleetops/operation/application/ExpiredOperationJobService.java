package com.dbfleetops.operation.application;

import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ExpiredOperationJobService {
    private final OperationJobRepository jobRepository;
    private final OperationTaskRepository taskRepository;
    private final OperationJobLeaseProperties properties;
    private final AuditRecorderPort audit;
    private final Clock clock;

    public ExpiredOperationJobService(OperationJobRepository jobRepository,
            OperationTaskRepository taskRepository, OperationJobLeaseProperties properties,
            AuditRecorderPort audit, Clock clock) {
        this.jobRepository = jobRepository;
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public int recoverExpiredJobs() {
        LocalDateTime now = LocalDateTime.now(clock);
        var jobs = jobRepository.findExpiredForUpdate(JobStatus.RUNNING, now,
                PageRequest.of(0, properties.expirationBatchSize()));
        for (var job : jobs) reconcile(job, now);
        return jobs.size();
    }

    private void reconcile(com.dbfleetops.operation.domain.OperationJob job, LocalDateTime now) {
        var tasks = taskRepository.findByOperationJobIdOrderByCreatedAtAsc(job.getId());
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
        if (tasks.stream().anyMatch(task -> task.getStatus() == OperationTaskStatus.TIMED_OUT)) {
            job.timeout("TASK_TIMED_OUT", "A linked Task timed out.");
            record(job.getId(), "JOB_TIMED_OUT", "Linked Task timed out.");
            return;
        }
        if (tasks.stream().anyMatch(task -> task.getStatus() == OperationTaskStatus.FAILED)) {
            job.fail("TASK_FAILED", "A linked Task failed.");
            record(job.getId(), "JOB_FAILED", "Linked Task failed.");
            return;
        }
        job.fail("JOB_WORKFLOW_INCONSISTENT",
                "All linked Tasks are terminal but the Job is still RUNNING.");
        record(job.getId(), "JOB_FAILED", "Workflow state is inconsistent.");
    }

    private void record(Long jobId, String action, String message) {
        audit.record("job-lease-reaper", action, "OPERATION_JOB", String.valueOf(jobId),
                "SUCCESS", message);
    }
}
