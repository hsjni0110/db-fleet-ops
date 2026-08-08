package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.application.workflow.backup.BackupWorkflow;
import com.dbfleetops.operation.domain.*;
import com.dbfleetops.operation.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackupJobOperationTaskFlowTest {
    private final AgentReader agents = mock(AgentReader.class);
    private final DatabaseReader databases = mock(DatabaseReader.class);
    private final CredentialReader credentials = mock(CredentialReader.class);
    private final TaskStore tasks = mock(TaskStore.class);
    private final JobStore jobs = mock(JobStore.class);
    private final BackupVerificationWriter verifications = mock(BackupVerificationWriter.class);

    @Test
    void backupSuccessCreatesOneRestoreVerificationTask() {
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "user", "key");
        ReflectionTestUtils.setField(job, "id", 100L);
        job.start("worker", LocalDateTime.now().plusMinutes(1));
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "token")).thenReturn(true);
        when(databases.findDatabase(1L)).thenReturn(Optional.of(new DatabaseExecutionTarget(
                1L, "orders", "mysql", 3306, "MYSQL", 1L, true)));
        when(credentials.findCredentialByDatabase(1L)).thenReturn(Optional.of(new CredentialReference(7L, 1L)));
        when(jobs.findByIdForUpdate(100L)).thenReturn(Optional.of(job));
        List<OperationTask> saved = new ArrayList<>();
        when(tasks.save(any())).thenAnswer(call -> {
            OperationTask task = call.getArgument(0);
            ReflectionTestUtils.setField(task, "id", 10L + saved.size());
            saved.add(task); return task;
        });
        JobTaskCoordinator coordinator = new JobTaskCoordinator(jobs, tasks);
        BackupPayloadBuilder payloads = mock(BackupPayloadBuilder.class);
        when(payloads.shouldVerifyAfterBackup(anyString())).thenReturn(true);
        when(payloads.createRestorePayload(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn("{}");
        BackupWorkflow workflow = new BackupWorkflow(agents, databases, credentials, tasks,
                coordinator, payloads, verifications);
        OperationTask backup = toEntity(workflow.createBackupTask(100L, 1L), saved);
        backup.claim(LocalDateTime.now(), LocalDateTime.now().plusMinutes(1));
        when(tasks.findById(backup.getId())).thenReturn(Optional.of(backup));
        TaskReportService reports = new TaskReportService(agents, tasks,
                new TaskResultDispatcher(List.of(workflow)), coordinator, Clock.systemUTC(),
                new OperationTaskResultFingerprint());

        reports.completeTask(1L, backup.getId(), new CompleteOperationTaskRequest("token", """
                {"status":"VERIFIED","backupFile":"/tmp/orders.sql"}
                """));

        assertThat(saved).hasSize(2);
        assertThat(saved.get(1).getTaskType()).isEqualTo(OperationTaskType.MYSQL_RESTORE_VERIFY);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void failedTaskFailsLinkedJob() {
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "user", "key");
        ReflectionTestUtils.setField(job, "id", 100L);
        job.start("worker", LocalDateTime.now().plusMinutes(1));
        OperationTask task = OperationTask.createForJob(1L, 100L,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        task.claim(LocalDateTime.now(), LocalDateTime.now().plusMinutes(1));
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "token")).thenReturn(true);
        when(tasks.findById(10L)).thenReturn(Optional.of(task));
        when(jobs.findByIdForUpdate(100L)).thenReturn(Optional.of(job));
        JobTaskCoordinator coordinator = new JobTaskCoordinator(jobs, tasks);
        TaskReportService reports = new TaskReportService(agents, tasks,
                new TaskResultDispatcher(List.of()), coordinator, Clock.systemUTC(),
                new OperationTaskResultFingerprint());

        reports.failTask(1L, 10L, new FailOperationTaskRequest("token", "BACKUP_FAILED", "failed"));

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getResultCode()).isEqualTo("BACKUP_FAILED");
    }

    private OperationTask toEntity(OperationTaskResponse ignored, List<OperationTask> saved) {
        return saved.getFirst();
    }
}
