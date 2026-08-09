package com.dbfleetops.operation.workflow.domain;

import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskStatus;

import java.util.List;

import static org.springframework.util.Assert.notNull;

/** 만료된 Job과 연결 Task를 보고 Job에 필요한 후속 행동을 결정합니다. */
public class ExpiredJobRule {

    public Decision decide(OperationJob job, List<OperationTask> tasks) {
        notNull(job, "Job은 필수입니다.");
        notNull(tasks, "연결 Task 목록은 필수입니다.");

        if (hasActiveTask(tasks)) {
            return Decision.EXTEND_LEASE;
        }
        if (tasks.isEmpty()) {
            return job.hasRemainingRetries() ? Decision.REQUEUE : Decision.TIMEOUT;
        }
        if (hasTaskInStatus(tasks, OperationTaskStatus.TIMED_OUT)) {
            return Decision.TIMEOUT_FROM_TASK;
        }
        if (hasTaskInStatus(tasks, OperationTaskStatus.FAILED)) {
            return Decision.FAIL_FROM_TASK;
        }
        return Decision.FAIL_INCONSISTENT;
    }

    private boolean hasActiveTask(List<OperationTask> tasks) {
        return tasks.stream().anyMatch(task -> task.getStatus() == OperationTaskStatus.QUEUED
                || task.getStatus() == OperationTaskStatus.RUNNING);
    }

    private boolean hasTaskInStatus(List<OperationTask> tasks, OperationTaskStatus status) {
        return tasks.stream().anyMatch(task -> task.getStatus() == status);
    }

    public enum Decision {
        EXTEND_LEASE,
        REQUEUE,
        TIMEOUT,
        TIMEOUT_FROM_TASK,
        FAIL_FROM_TASK,
        FAIL_INCONSISTENT
    }
}
