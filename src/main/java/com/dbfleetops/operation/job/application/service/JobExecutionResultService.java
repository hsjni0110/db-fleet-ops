package com.dbfleetops.operation.job.application.service;

import com.dbfleetops.operation.job.application.execution.JobExecutionOutcome;
import com.dbfleetops.operation.job.application.service.OperationJobLeaseProperties;
import com.dbfleetops.operation.shared.application.required.AuditWriter;
import com.dbfleetops.operation.job.domain.JobRetryPolicy;
import com.dbfleetops.operation.job.domain.OperationJob;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** Job 실행 결과를 상태와 감사 기록에 일관되게 반영합니다. */
@Component
public class JobExecutionResultService {

    private final AuditWriter audit;
    private final Clock clock;
    private final OperationJobLeaseProperties lease;
    private final JobRetryPolicy retryPolicy = new JobRetryPolicy();

    public JobExecutionResultService(AuditWriter audit, Clock clock,
            OperationJobLeaseProperties lease) {
        this.audit = audit;
        this.clock = clock;
        this.lease = lease;
    }

    public void apply(String workerId, OperationJob job, JobExecutionOutcome outcome) {
        switch (outcome.status()) {
            case IN_PROGRESS -> record(workerId, job, "OPERATION_TASK_CREATED", "SUCCESS",
                    outcome.resultMessage());
            case SUCCEEDED -> succeed(workerId, job, outcome.resultMessage(),
                    completedAction(job));
            case FAILED -> fail(workerId, job, outcome.resultCode(), outcome.resultMessage(),
                    outcome.retryable());
        }
    }

    public void succeed(String workerId, OperationJob job, String resultMessage) {
        succeed(workerId, job, resultMessage, "JOB_SUCCEEDED");
    }

    public void fail(String workerId, OperationJob job, String resultCode, String resultMessage,
            boolean retryable) {
        job.fail(now(), resultCode, resultMessage);
        record(workerId, job, "JOB_FAILED", "FAILED", resultMessage);

        if (!retryPolicy.canRetry(job, retryable)) {
            return;
        }

        LocalDateTime retriedAt = now();
        job.retry(retriedAt, retriedAt.plus(lease.retryDelay()));
        record(workerId, job, "JOB_RETRIED", "SUCCESS",
                "Job re-queued. retryCount=" + job.getRetryCount());
    }

    private void succeed(String workerId, OperationJob job, String resultMessage, String action) {
        job.succeed(now(), resultMessage);
        record(workerId, job, action, "SUCCESS", resultMessage);
    }

    private String completedAction(OperationJob job) {
        return switch (job.getJobType()) {
            case CONFIGURATION_CHECK -> "CONFIGURATION_CHECK_COMPLETED";
            case CONFIGURATION_APPLY -> "CONFIGURATION_APPLY_COMPLETED";
            case BACKUP -> "JOB_SUCCEEDED";
        };
    }

    private void record(String workerId, OperationJob job, String action, String result,
            String message) {
        audit.record(workerId, action, "OPERATION_JOB", String.valueOf(job.getId()), result,
                message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
