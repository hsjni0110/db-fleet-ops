package com.dbfleetops.operation.workflow.application.expiration;

import com.dbfleetops.operation.job.application.service.OperationJobLeaseProperties;
import com.dbfleetops.operation.workflow.application.JobTaskCoordinator;

import com.dbfleetops.operation.shared.application.required.AuditWriter;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.task.application.required.TaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredJobServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    @Mock JobStore jobs;
    @Mock TaskStore tasks;
    @Mock AuditWriter audit;
    private ExpiredJobService service;

    @BeforeEach
    void setUp() {
        service = new ExpiredJobService(jobs, tasks,
                new OperationJobLeaseProperties(Duration.ofSeconds(60), Duration.ofSeconds(5),
                        Duration.ofSeconds(30), 100, true), audit, CLOCK,
                new JobTaskCoordinator(jobs, tasks, CLOCK));
    }

    @Test
    void keepsRunningJobWhenAnActiveTaskExists() {
        OperationJob job = runningJob();
        OperationTask task = OperationTask.createForJob(1L, job.getId(),
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        when(jobs.findExpiredForUpdate(eq(JobStatus.RUNNING), any(), eq(100))).thenReturn(List.of(job));
        when(tasks.findByJob(job.getId())).thenReturn(List.of(task));

        service.recoverExpiredJobs();

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getLeaseUntil()).isEqualTo(LocalDateTime.now(CLOCK).plusSeconds(60));
    }

    @Test
    void requeuesOrphanAndEventuallyTimesItOut() {
        OperationJob job = runningJob();
        when(jobs.findExpiredForUpdate(eq(JobStatus.RUNNING), any(), eq(100))).thenReturn(List.of(job));
        when(tasks.findByJob(job.getId())).thenReturn(List.of());

        service.recoverExpiredJobs();
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getRetryCount()).isEqualTo(1);

        ReflectionTestUtils.setField(job, "status", JobStatus.RUNNING);
        ReflectionTestUtils.setField(job, "retryCount", job.getMaxRetryCount());
        service.recoverExpiredJobs();
        assertThat(job.getStatus()).isEqualTo(JobStatus.TIMED_OUT);
    }

    private OperationJob runningJob() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        OperationJob job = OperationJob.create(
                JobType.BACKUP, 1L, "user", "lease-test", now.minusMinutes(1));
        ReflectionTestUtils.setField(job, "id", 10L);
        job.start("worker", now.minusMinutes(1), now.minusSeconds(1));
        return job;
    }
}
