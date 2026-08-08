package com.dbfleetops.operation.job.application.service;

import com.dbfleetops.operation.job.application.execution.JobExecutionDispatcher;
import com.dbfleetops.operation.job.application.execution.JobExecutionOutcome;

import com.dbfleetops.operation.job.application.service.OperationJobLeaseProperties;
import com.dbfleetops.operation.shared.application.required.AuditWriter;
import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.application.required.WorkerState;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.job.dto.FailJobRequest;
import com.dbfleetops.operation.job.dto.SucceedJobRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobClaimAndReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    private JobStore jobs;
    private AuditWriter audit;
    private WorkerState workerState;
    private JobExecutionDispatcher executions;
    private JobClaimService claims;
    private JobReportService reports;

    @BeforeEach
    void setUp() {
        jobs = mock(JobStore.class);
        audit = mock(AuditWriter.class);
        workerState = mock(WorkerState.class);
        executions = mock(JobExecutionDispatcher.class);
        OperationJobLeaseProperties lease = new OperationJobLeaseProperties(
                Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofSeconds(30), 100, true);
        JobExecutionResultService results = new JobExecutionResultService(audit, CLOCK, lease);
        claims = new JobClaimService(jobs, workerState, executions, results, audit, CLOCK, lease);
        reports = new JobReportService(jobs, results);
    }

    @Test
    void claimsWaitingJobAndStartsItsExecution() {
        OperationJob job = queuedJob(JobType.BACKUP);
        when(jobs.findClaimable(eq(JobStatus.QUEUED), any(), eq(10)))
                .thenReturn(List.of(job));
        when(executions.execute(job)).thenReturn(
                JobExecutionOutcome.inProgress("Backup operation task created."));

        var response = claims.claimJob("worker");

        assertThat(response.claimed()).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getLeaseOwner()).isEqualTo("worker");
        verify(executions).execute(job);
    }

    @Test
    void shuttingDownWorkerDoesNotClaimJob() {
        when(workerState.isShuttingDown()).thenReturn(true);

        assertThat(claims.claimJob("worker").claimed()).isFalse();

        verify(jobs, never()).findClaimable(any(), any(), any(Integer.class));
    }

    @Test
    void successfulExecutionCompletesClaimedJob() {
        OperationJob job = queuedJob(JobType.CONFIGURATION_CHECK);
        when(jobs.findClaimable(eq(JobStatus.QUEUED), any(), eq(10)))
                .thenReturn(List.of(job));
        when(executions.execute(job)).thenReturn(JobExecutionOutcome.succeeded("점검 완료"));

        claims.claimJob("worker");

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getResultMessage()).isEqualTo("점검 완료");
    }

    @Test
    void ownerCanReportSuccess() {
        OperationJob job = runningJob("worker");
        when(jobs.findById(1L)).thenReturn(Optional.of(job));

        reports.succeedJob("worker", 1L, new SucceedJobRequest("완료"));

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void retryableFailureQueuesJobAgain() {
        OperationJob job = runningJob("worker");
        when(jobs.findById(1L)).thenReturn(Optional.of(job));

        reports.failJob("worker", 1L,
                new FailJobRequest("TEMPORARY", "일시 오류", true));

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getRetryCount()).isEqualTo(1);
    }

    private OperationJob queuedJob(JobType type) {
        return OperationJob.create(type, 1L, "user", "key", "{}",
                LocalDateTime.now(CLOCK));
    }

    private OperationJob runningJob(String workerId) {
        OperationJob job = queuedJob(JobType.BACKUP);
        LocalDateTime now = LocalDateTime.now(CLOCK);
        job.start(workerId, now, now.plusMinutes(1));
        return job;
    }
}
