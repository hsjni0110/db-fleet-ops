package com.dbfleetops.operation.workflow.application;

import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.task.application.required.LinkedJobFailure;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskStatus;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LinkedJobFailureTransactionTest {

    @Autowired
    private JobStore jobs;

    @Autowired
    private TaskStore tasks;

    @Autowired
    private LinkedJobFailure linkedJobFailure;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void taskAndJobFailureAreRolledBackTogether() {
        LocalDateTime now = LocalDateTime.now();
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "test",
                UUID.randomUUID().toString(), now);
        job.start("worker", now, now.plusMinutes(1));
        job = jobs.save(job);

        OperationTask task = OperationTask.createForJob(1L, job.getId(),
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        task.claim(now, now.plusMinutes(1));
        task = tasks.save(task);

        Long jobId = job.getId();
        Long taskId = task.getId();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            OperationTask storedTask = tasks.findById(taskId).orElseThrow();
            storedTask.acceptFailureReport(1, UUID.randomUUID().toString(), "a".repeat(64),
                    "BACKUP_FAILED", "백업 실패", now.plusSeconds(1));
            linkedJobFailure.fail(jobId, "BACKUP_FAILED", "백업 실패");
            throw new IllegalStateException("강제 Rollback");
        })).isInstanceOf(IllegalStateException.class);

        OperationTaskStatus storedTaskStatus = transactions.execute(status ->
                tasks.findById(taskId).orElseThrow().getStatus());
        assertThat(storedTaskStatus).isEqualTo(OperationTaskStatus.RUNNING);
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.RUNNING);
    }
}
