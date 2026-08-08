package com.dbfleetops.operation.workflow.application;

import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.task.application.required.LinkedJobProgress;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.workflow.domain.JobProgressPolicy;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** Task 결과를 연결된 Job 상태에 일관되게 반영합니다. */
@Component
public class JobTaskCoordinator implements LinkedJobProgress {
    private final JobStore jobs;
    private final TaskStore tasks;
    private final Clock clock;
    private final JobProgressPolicy policy = new JobProgressPolicy();
    public JobTaskCoordinator(JobStore jobs, TaskStore tasks, Clock clock) {
        this.jobs = jobs;
        this.tasks = tasks;
        this.clock = clock;
    }

    public OperationJob linkedJobForUpdate(OperationTask task) {
        if (task.getOperationJobId() == null) return null;
        return jobs.findByIdForUpdate(task.getOperationJobId()).orElseThrow(() ->
                new IllegalArgumentException("Operation job not found. operationJobId="
                        + task.getOperationJobId()));
    }

    public void failLinkedJob(OperationTask task, String code, String message) {
        fail(task.getOperationJobId(), code, message);
    }

    @Override
    public void fail(Long jobId, String code, String message) {
        OperationJob job = linkedJobForUpdate(jobId);
        if (job != null && job.getStatus() == JobStatus.RUNNING) job.fail(now(), code, message);
    }

    public void timeoutLinkedJob(OperationTask task, String code, String message) {
        timeout(task.getOperationJobId(), code, message);
    }

    @Override
    public void timeout(Long jobId, String code, String message) {
        OperationJob job = linkedJobForUpdate(jobId);
        if (job != null && job.getStatus() == JobStatus.RUNNING) job.timeout(now(), code, message);
    }

    private OperationJob linkedJobForUpdate(Long jobId) {
        if (jobId == null) return null;
        return jobs.findByIdForUpdate(jobId).orElseThrow(() ->
                new IllegalArgumentException("Operation job not found. operationJobId=" + jobId));
    }

    public void reconcile(OperationJob job) {
        switch (policy.evaluate(tasks.findByJob(job.getId()))) {
            case KEEP_RUNNING -> { }
            case FAILED -> job.fail(now(), "TASK_FAILED", "A linked Task failed.");
            case TIMED_OUT -> job.timeout(now(), "TASK_TIMED_OUT", "A linked Task timed out.");
            case INCONSISTENT -> job.fail(now(), "JOB_WORKFLOW_INCONSISTENT",
                    "All linked Tasks are terminal but the Job is still RUNNING.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
