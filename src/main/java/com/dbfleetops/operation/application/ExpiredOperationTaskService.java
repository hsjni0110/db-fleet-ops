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
public class ExpiredOperationTaskService {
    private final OperationTaskRepository taskRepository;
    private final OperationJobRepository jobRepository;
    private final OperationTaskLeaseProperties properties;
    private final AuditRecorderPort auditRecorder;
    private final Clock clock;

    public ExpiredOperationTaskService(OperationTaskRepository taskRepository,
            OperationJobRepository jobRepository, OperationTaskLeaseProperties properties,
            AuditRecorderPort auditRecorder, Clock clock) {
        this.taskRepository = taskRepository;
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public int recoverExpiredTasks() {
        LocalDateTime now = LocalDateTime.now(clock);
        var tasks = taskRepository.findExpiredForUpdate(OperationTaskStatus.RUNNING, now,
                PageRequest.of(0, properties.expirationBatchSize()));
        for (var task : tasks) {
            if (task.getExecutionAttempt() < properties.maximumAttempts()) {
                task.requeueExpiredLease(now, properties.maximumAttempts());
                auditRecorder.record("task-lease-reaper", "OPERATION_TASK_REQUEUED",
                        "OPERATION_TASK", String.valueOf(task.getId()), "SUCCESS",
                        "Expired task re-queued. executionAttempt=" + task.getExecutionAttempt());
            } else {
                task.timeoutExpiredLease(now, properties.maximumAttempts());
                timeoutLinkedJob(task.getOperationJobId());
                auditRecorder.record("task-lease-reaper", "OPERATION_TASK_TIMED_OUT",
                        "OPERATION_TASK", String.valueOf(task.getId()), "SUCCESS",
                        "Task timed out after maximum execution attempts.");
            }
        }
        return tasks.size();
    }

    private void timeoutLinkedJob(Long jobId) {
        if (jobId == null) return;
        jobRepository.findById(jobId).filter(job -> job.getStatus() == JobStatus.RUNNING)
                .ifPresent(job -> job.timeout("TASK_LEASE_EXPIRED",
                        "Operation task lease expired after maximum execution attempts."));
    }
}
