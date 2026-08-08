package com.dbfleetops.operation.workflow.domain;

import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskStatus;
import java.util.List;

/** 연결된 Task 상태만 보고 Job을 유지하거나 종료할지를 판단합니다. */
public class JobProgressPolicy {
    public Decision evaluate(List<OperationTask> tasks) {
        if (tasks.stream().anyMatch(this::isActive)) return Decision.KEEP_RUNNING;
        if (tasks.stream().anyMatch(task -> task.getStatus() == OperationTaskStatus.TIMED_OUT))
            return Decision.TIMED_OUT;
        if (tasks.stream().anyMatch(task -> task.getStatus() == OperationTaskStatus.FAILED))
            return Decision.FAILED;
        return Decision.INCONSISTENT;
    }
    private boolean isActive(OperationTask task) {
        return task.getStatus() == OperationTaskStatus.QUEUED
                || task.getStatus() == OperationTaskStatus.RUNNING;
    }
    public enum Decision { KEEP_RUNNING, FAILED, TIMED_OUT, INCONSISTENT }
}
