package com.dbfleetops.operation.workflow.domain;

import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskStatus;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.dbfleetops.operation.workflow.domain.ExpiredJobRule.Decision.EXTEND_LEASE;
import static com.dbfleetops.operation.workflow.domain.ExpiredJobRule.Decision.FAIL_FROM_TASK;
import static com.dbfleetops.operation.workflow.domain.ExpiredJobRule.Decision.FAIL_INCONSISTENT;
import static com.dbfleetops.operation.workflow.domain.ExpiredJobRule.Decision.REQUEUE;
import static com.dbfleetops.operation.workflow.domain.ExpiredJobRule.Decision.TIMEOUT;
import static com.dbfleetops.operation.workflow.domain.ExpiredJobRule.Decision.TIMEOUT_FROM_TASK;
import static org.assertj.core.api.Assertions.assertThat;

class ExpiredJobRuleTest {

    private final ExpiredJobRule rule = new ExpiredJobRule();

    @Test
    void activeTaskExtendsLeaseFirst() {
        assertThat(rule.decide(jobWithRemainingRetries(), List.of(task(OperationTaskStatus.RUNNING),
                task(OperationTaskStatus.FAILED)))).isEqualTo(EXTEND_LEASE);
    }

    @Test
    void jobWithoutTasksIsRequeuedWhileRetriesRemain() {
        assertThat(rule.decide(jobWithRemainingRetries(), List.of())).isEqualTo(REQUEUE);
    }

    @Test
    void jobWithoutTasksTimesOutAfterRetriesAreExhausted() {
        OperationJob job = jobWithRemainingRetries();
        ReflectionTestUtils.setField(job, "retryCount", job.getMaxRetryCount());

        assertThat(rule.decide(job, List.of())).isEqualTo(TIMEOUT);
    }

    @Test
    void timedOutTaskTakesPriorityOverFailedTask() {
        assertThat(rule.decide(jobWithRemainingRetries(), List.of(
                task(OperationTaskStatus.FAILED), task(OperationTaskStatus.TIMED_OUT))))
                .isEqualTo(TIMEOUT_FROM_TASK);
    }

    @Test
    void failedTaskFailsJob() {
        assertThat(rule.decide(jobWithRemainingRetries(),
                List.of(task(OperationTaskStatus.FAILED)))).isEqualTo(FAIL_FROM_TASK);
    }

    @Test
    void succeededTasksWithoutWorkflowCompletionAreInconsistent() {
        assertThat(rule.decide(jobWithRemainingRetries(),
                List.of(task(OperationTaskStatus.SUCCEEDED)))).isEqualTo(FAIL_INCONSISTENT);
    }

    private OperationJob jobWithRemainingRetries() {
        return OperationJob.create(JobType.BACKUP, 1L, "user", "key", LocalDateTime.now());
    }

    private OperationTask task(OperationTaskStatus status) {
        OperationTask task = OperationTask.create(1L,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        ReflectionTestUtils.setField(task, "status", status);
        return task;
    }
}
