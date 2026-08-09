package com.dbfleetops.operation.workflow.backup;

import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.shared.application.required.CredentialReader;
import com.dbfleetops.operation.shared.application.required.DatabaseReader;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import com.dbfleetops.operation.workflow.application.required.BackupPayloadBuilder;
import com.dbfleetops.operation.workflow.application.required.BackupVerificationWriter;
import com.dbfleetops.operation.workflow.application.required.RestoreVerificationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupWorkflowResultTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    private final JobStore jobs = mock(JobStore.class);
    private final TaskStore tasks = mock(TaskStore.class);
    private final BackupPayloadBuilder payloads = mock(BackupPayloadBuilder.class);
    private final BackupVerificationWriter verifications = mock(BackupVerificationWriter.class);
    private final BackupWorkflow workflow = new BackupWorkflow(mock(AgentReader.class),
            mock(DatabaseReader.class), mock(CredentialReader.class), jobs, tasks, payloads,
            verifications, CLOCK);

    @Test
    void succeedsJobWhenBackupDoesNotRequireRestoreVerification() {
        OperationJob job = runningJob();
        OperationTask task = linkedTask(OperationTaskType.MYSQL_LOGICAL_BACKUP);
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(payloads.shouldVerifyAfterBackup(task.getParametersJson())).thenReturn(false);

        workflow.continueAfterSuccess(task.getId(), "{\"backupFile\":\"orders.sql\"}");

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void succeedsJobWhenRestoreVerificationPasses() {
        OperationJob job = runningJob();
        OperationTask task = linkedTask(OperationTaskType.MYSQL_RESTORE_VERIFY);
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(verifications.record(job.getId(), task.getId(), "{\"verified\":true}"))
                .thenReturn(new RestoreVerificationOutcome(true, null, null));

        workflow.continueAfterSuccess(task.getId(), "{\"verified\":true}");

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void failsJobWhenRestoreVerificationDoesNotPass() {
        OperationJob job = runningJob();
        OperationTask task = linkedTask(OperationTaskType.MYSQL_RESTORE_VERIFY);
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(verifications.record(job.getId(), task.getId(), "{\"verified\":false}"))
                .thenReturn(new RestoreVerificationOutcome(false, "RESTORE_VERIFY_FAILED",
                        "복원 검증에 실패했습니다."));

        workflow.continueAfterSuccess(task.getId(), "{\"verified\":false}");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getResultCode()).isEqualTo("RESTORE_VERIFY_FAILED");
    }

    private OperationJob runningJob() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "user", "key", now);
        ReflectionTestUtils.setField(job, "id", 100L);
        job.start("worker", now.minusMinutes(1), now.plusMinutes(1));
        return job;
    }

    private OperationTask linkedTask(OperationTaskType type) {
        OperationTask task = OperationTask.createForJob(1L, 100L, type, "{}");
        ReflectionTestUtils.setField(task, "id", 10L);
        return task;
    }
}
