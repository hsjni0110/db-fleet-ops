package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.BackupTasks;
import com.dbfleetops.operation.application.required.AuditWriter;
import com.dbfleetops.operation.application.required.ConfigurationApplyOutcome;
import com.dbfleetops.operation.application.required.ConfigurationCheckOutcome;
import com.dbfleetops.operation.application.required.ConfigurationJobRunner;
import com.dbfleetops.operation.application.required.JobStore;
import com.dbfleetops.operation.application.required.WorkerState;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.dto.FailJobRequest;
import com.dbfleetops.operation.dto.SucceedJobRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    private JobStore jobs;
    private AuditWriter audit;
    private BackupTasks backups;
    private ConfigurationJobRunner configurations;
    private WorkerState workerState;
    private WorkerService service;

    @BeforeEach
    void setUp() {
        jobs = mock(JobStore.class);
        audit = mock(AuditWriter.class);
        backups = mock(BackupTasks.class);
        configurations = mock(ConfigurationJobRunner.class);
        workerState = mock(WorkerState.class);

        OperationJobLeaseProperties lease = new OperationJobLeaseProperties(
                Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofSeconds(30), 100, true);

        service = new WorkerService(jobs, audit, backups, configurations, workerState,
                CLOCK, lease, new WorkerResultMessageFactory());
    }

    @Test
    void backupJobCreatesAgentTaskAndStaysRunning() {
        OperationJob job = queuedJob(JobType.BACKUP);
        waitingJob(job);

        ClaimJobResponse response = service.claimJob("worker");

        verify(backups).createBackupTask(job.getId(), 1L);
        assertThat(response.claimed()).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getLeaseOwner()).isEqualTo("worker");
        assertThat(job.getLeaseUntil()).isEqualTo(LocalDateTime.now(CLOCK).plusSeconds(60));
    }

    @Test
    void shuttingDownWorkerDoesNotClaimJob() {
        when(workerState.isShuttingDown()).thenReturn(true);

        ClaimJobResponse response = service.claimJob("worker");

        assertThat(response.claimed()).isFalse();
        verify(jobs, never()).findClaimable(any(), any(), any(Integer.class));
    }

    @Test
    void emptyQueueReturnsEmptyResponse() {
        when(jobs.findClaimable(eq(JobStatus.QUEUED), any(), eq(10))).thenReturn(List.of());

        ClaimJobResponse response = service.claimJob("worker");

        assertThat(response.claimed()).isFalse();
    }

    @Test
    void configurationCheckOutcomeCompletesJob() {
        OperationJob job = queuedJob(JobType.CONFIGURATION_CHECK);
        waitingJob(job);
        when(configurations.check(job)).thenReturn(new ConfigurationCheckOutcome(3L, "COMPLIANT"));

        service.claimJob("worker");

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getResultMessage()).contains("driftId=3", "status=COMPLIANT");
    }

    @Test
    void failedConfigurationCheckIsQueuedForRetry() {
        OperationJob job = queuedJob(JobType.CONFIGURATION_CHECK);
        waitingJob(job);
        when(configurations.check(job)).thenThrow(new IllegalStateException("점검 실패"));

        service.claimJob("worker");

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getAvailableAt()).isEqualTo(LocalDateTime.now(CLOCK).plusSeconds(30));
    }

    @Test
    void successfulConfigurationApplyCompletesJob() {
        OperationJob job = queuedJob(JobType.CONFIGURATION_APPLY);
        waitingJob(job);
        when(configurations.apply(job)).thenReturn(
                new ConfigurationApplyOutcome(7L, true, "SUCCEEDED", 2, 0, 0));

        service.claimJob("worker");

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getResultMessage()).contains("applyId=7", "successCount=2");
    }

    @Test
    void unsuccessfulConfigurationApplyDoesNotRetry() {
        OperationJob job = queuedJob(JobType.CONFIGURATION_APPLY);
        waitingJob(job);
        when(configurations.apply(job)).thenReturn(
                new ConfigurationApplyOutcome(7L, false, "FAILED", 1, 1, 0));

        service.claimJob("worker");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getRetryCount()).isZero();
        assertThat(job.getResultCode()).isEqualTo("CONFIGURATION_APPLY_FAILED");
    }

    @Test
    void ownerCanReportJobSuccess() {
        OperationJob job = runningJob("worker");
        when(jobs.findById(1L)).thenReturn(Optional.of(job));

        service.succeedJob("worker", 1L, new SucceedJobRequest("완료"));

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getResultMessage()).isEqualTo("완료");
    }

    @Test
    void retryableFailureQueuesJobAgain() {
        OperationJob job = runningJob("worker");
        when(jobs.findById(1L)).thenReturn(Optional.of(job));

        service.failJob("worker", 1L, new FailJobRequest("TEMPORARY", "일시 오류", true));

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getRetryCount()).isEqualTo(1);
    }

    @Test
    void anotherWorkerCannotReportResult() {
        OperationJob job = runningJob("worker-a");
        when(jobs.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.succeedJob(
                "worker-b", 1L, new SucceedJobRequest("완료")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Job 실행권을 가진 Worker");
    }

    @Test
    void blankWorkerIdIsRejectedBeforeStoreLookup() {
        assertThatThrownBy(() -> service.claimJob(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Worker ID");

        verify(jobs, never()).findClaimable(any(), any(), any(Integer.class));
    }

    private OperationJob queuedJob(JobType type) {
        return OperationJob.create(type, 1L, "user", "key", "{}");
    }

    private OperationJob runningJob(String workerId) {
        OperationJob job = queuedJob(JobType.CONFIGURATION_CHECK);
        job.start(workerId, LocalDateTime.now(CLOCK).plusMinutes(1));
        return job;
    }

    private void waitingJob(OperationJob job) {
        when(jobs.findClaimable(eq(JobStatus.QUEUED), any(), eq(10))).thenReturn(List.of(job));
    }
}
