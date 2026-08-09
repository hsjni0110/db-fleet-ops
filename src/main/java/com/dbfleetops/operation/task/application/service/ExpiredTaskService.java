package com.dbfleetops.operation.task.application.service;

import com.dbfleetops.operation.task.application.provided.ExpiredTasks;
import com.dbfleetops.operation.shared.application.required.AuditWriter;
import com.dbfleetops.operation.task.application.required.LinkedJobFailure;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ExpiredTaskService implements ExpiredTasks {
    private final TaskStore taskRepository;
    private final OperationTaskLeaseProperties properties;
    private final AuditWriter auditRecorder;
    private final LinkedJobFailure linkedJobFailure;
    private final Clock clock;

    public ExpiredTaskService(TaskStore taskRepository,
            OperationTaskLeaseProperties properties,
            AuditWriter auditRecorder, Clock clock, LinkedJobFailure linkedJobFailure) {
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.linkedJobFailure = linkedJobFailure;
    }

    @Transactional
    public int recoverExpiredTasks() {
        LocalDateTime now = LocalDateTime.now(clock);
        var tasks = taskRepository.findExpiredForUpdate(OperationTaskStatus.RUNNING, now,
                properties.expirationBatchSize());
        for (var task : tasks) {
            if (task.getExecutionAttempt() < properties.maximumAttempts()) {
                task.requeueExpiredLease(now, properties.maximumAttempts());
                auditRecorder.record("task-lease-reaper", "OPERATION_TASK_REQUEUED",
                        "OPERATION_TASK", String.valueOf(task.getId()), "SUCCESS",
                        "Expired task re-queued. executionAttempt=" + task.getExecutionAttempt());
            } else {
                task.timeoutExpiredLease(now, properties.maximumAttempts());
                linkedJobFailure.timeout(task.getOperationJobId(), "TASK_LEASE_EXPIRED",
                        "Operation task lease expired after maximum execution attempts.");
                auditRecorder.record("task-lease-reaper", "OPERATION_TASK_TIMED_OUT",
                        "OPERATION_TASK", String.valueOf(task.getId()), "SUCCESS",
                        "Task timed out after maximum execution attempts.");
            }
        }
        return tasks.size();
    }

}
