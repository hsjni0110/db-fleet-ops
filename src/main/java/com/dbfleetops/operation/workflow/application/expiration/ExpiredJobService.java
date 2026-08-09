package com.dbfleetops.operation.workflow.application.expiration;

import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.application.service.OperationJobLeaseProperties;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.shared.application.required.AuditWriter;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.workflow.application.provided.ExpiredJobs;
import com.dbfleetops.operation.workflow.domain.ExpiredJobRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 만료된 Job을 조회하고 도메인 규칙이 결정한 복구 행동을 반영합니다. */
@Service
public class ExpiredJobService implements ExpiredJobs {
    private final JobStore jobs;
    private final TaskStore tasks;
    private final OperationJobLeaseProperties properties;
    private final AuditWriter audit;
    private final Clock clock;
    private final ExpiredJobRule rule = new ExpiredJobRule();

    public ExpiredJobService(JobStore jobs,
            TaskStore tasks, OperationJobLeaseProperties properties,
            AuditWriter audit, Clock clock) {
        this.jobs = jobs;
        this.tasks = tasks;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int recoverExpiredJobs() {
        LocalDateTime now = LocalDateTime.now(clock);
        var expiredJobs = jobs.findExpiredForUpdate(JobStatus.RUNNING, now,
                properties.expirationBatchSize());
        for (var job : expiredJobs) {
            recover(job, now);
        }
        return expiredJobs.size();
    }

    private void recover(OperationJob job, LocalDateTime now) {
        var linkedTasks = tasks.findByJob(job.getId());
        switch (rule.decide(job, linkedTasks)) {
            case EXTEND_LEASE -> extendLease(job, now);
            case REQUEUE -> requeue(job, now);
            case TIMEOUT -> timeoutWithoutTask(job, now);
            case TIMEOUT_FROM_TASK -> timeoutFromTask(job, now);
            case FAIL_FROM_TASK -> failFromTask(job, now);
            case FAIL_INCONSISTENT -> failInconsistent(job, now);
        }
    }

    private void extendLease(OperationJob job, LocalDateTime now) {
        job.extendLease(now, now.plus(properties.duration()));
        record(job.getId(), "JOB_LEASE_EXTENDED", "Active Task still exists.");
    }

    private void requeue(OperationJob job, LocalDateTime now) {
        job.requeueExpiredLease(now, now.plus(properties.retryDelay()));
        record(job.getId(), "JOB_REQUEUED", "Expired Job has no Task.");
    }

    private void timeoutWithoutTask(OperationJob job, LocalDateTime now) {
        job.timeout(now, "JOB_LEASE_EXPIRED", "Job lease expired after maximum retries.");
        record(job.getId(), "JOB_TIMED_OUT", "Maximum retries reached.");
    }

    private void timeoutFromTask(OperationJob job, LocalDateTime now) {
        job.timeout(now, "TASK_TIMED_OUT", "A linked Task timed out.");
        recordReconciled(job.getId());
    }

    private void failFromTask(OperationJob job, LocalDateTime now) {
        job.fail(now, "TASK_FAILED", "A linked Task failed.");
        recordReconciled(job.getId());
    }

    private void failInconsistent(OperationJob job, LocalDateTime now) {
        job.fail(now, "JOB_WORKFLOW_INCONSISTENT",
                "All linked Tasks are terminal but the Job is still RUNNING.");
        recordReconciled(job.getId());
    }

    private void recordReconciled(Long jobId) {
        record(jobId, "JOB_RECONCILED", "Job state reconciled from linked Tasks.");
    }

    private void record(Long jobId, String action, String message) {
        audit.record("job-lease-reaper", action, "OPERATION_JOB", String.valueOf(jobId),
                "SUCCESS", message);
    }
}
