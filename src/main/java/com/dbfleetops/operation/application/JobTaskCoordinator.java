package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.*;
import org.springframework.stereotype.Component;

/** Task 결과를 연결된 Job 상태에 일관되게 반영합니다. */
@Component
public class JobTaskCoordinator {
    private final JobStore jobs;
    private final TaskStore tasks;
    private final JobProgressPolicy policy = new JobProgressPolicy();
    public JobTaskCoordinator(JobStore jobs, TaskStore tasks) { this.jobs = jobs; this.tasks = tasks; }

    public OperationJob linkedJobForUpdate(OperationTask task) {
        if (task.getOperationJobId() == null) return null;
        return jobs.findByIdForUpdate(task.getOperationJobId()).orElseThrow(() ->
                new IllegalArgumentException("Operation job not found. operationJobId="
                        + task.getOperationJobId()));
    }

    public void failLinkedJob(OperationTask task, String code, String message) {
        OperationJob job = linkedJobForUpdate(task);
        if (job != null && job.getStatus() == JobStatus.RUNNING) job.fail(code, message);
    }

    public void timeoutLinkedJob(OperationTask task, String code, String message) {
        OperationJob job = linkedJobForUpdate(task);
        if (job != null && job.getStatus() == JobStatus.RUNNING) job.timeout(code, message);
    }

    public void reconcile(OperationJob job) {
        switch (policy.evaluate(tasks.findByJob(job.getId()))) {
            case KEEP_RUNNING -> { }
            case FAILED -> job.fail("TASK_FAILED", "A linked Task failed.");
            case TIMED_OUT -> job.timeout("TASK_TIMED_OUT", "A linked Task timed out.");
            case INCONSISTENT -> job.fail("JOB_WORKFLOW_INCONSISTENT",
                    "All linked Tasks are terminal but the Job is still RUNNING.");
        }
    }
}
