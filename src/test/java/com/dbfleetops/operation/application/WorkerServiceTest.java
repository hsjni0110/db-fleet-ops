package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.BackupTasks;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerServiceTest {
    @Test
    void backupJobCreatesAgentTaskAndStaysRunning() {
        JobStore jobs = mock(JobStore.class);
        BackupTasks backups = mock(BackupTasks.class);
        ConfigurationJobRunner configuration = mock(ConfigurationJobRunner.class);
        WorkerState state = mock(WorkerState.class);
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "user", "key");
        when(jobs.findClaimable(eq(JobStatus.QUEUED), any(), eq(10))).thenReturn(List.of(job));
        WorkerService service = new WorkerService(jobs, mock(AuditWriter.class), backups,
                configuration, state);
        service.claimJob("worker");
        verify(backups).createBackupTask(job.getId(), 1L);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void configurationCheckOutcomeCompletesJob() {
        JobStore jobs = mock(JobStore.class);
        ConfigurationJobRunner configuration = mock(ConfigurationJobRunner.class);
        OperationJob job = OperationJob.create(JobType.CONFIGURATION_CHECK, 1L, "user", "key", "{}");
        when(jobs.findClaimable(eq(JobStatus.QUEUED), any(), eq(10))).thenReturn(List.of(job));
        when(configuration.check(job)).thenReturn(new ConfigurationCheckOutcome(3L, "COMPLIANT"));
        WorkerService service = new WorkerService(jobs, mock(AuditWriter.class),
                mock(BackupTasks.class), configuration, mock(WorkerState.class));
        service.claimJob("worker");
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }
}
