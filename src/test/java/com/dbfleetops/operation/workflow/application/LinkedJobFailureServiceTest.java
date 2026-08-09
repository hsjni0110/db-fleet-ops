package com.dbfleetops.operation.workflow.application;

import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkedJobFailureServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    private final JobStore jobs = mock(JobStore.class);
    private final LinkedJobFailureService service = new LinkedJobFailureService(jobs, CLOCK);

    @Test
    void failsRunningJob() {
        OperationJob job = runningJob();
        when(jobs.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        service.fail(10L, "BACKUP_FAILED", "백업에 실패했습니다.");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getResultCode()).isEqualTo("BACKUP_FAILED");
        assertThat(job.getFinishedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    void timesOutRunningJob() {
        OperationJob job = runningJob();
        when(jobs.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        service.timeout(10L, "TASK_LEASE_EXPIRED", "Task 실행권이 만료됐습니다.");

        assertThat(job.getStatus()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(job.getResultCode()).isEqualTo("TASK_LEASE_EXPIRED");
    }

    @Test
    void ignoresStandaloneTaskWithoutJob() {
        service.fail(null, "TASK_FAILED", "실패했습니다.");

        verify(jobs, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotChangeFinishedJob() {
        OperationJob job = runningJob();
        job.succeed(LocalDateTime.now(CLOCK), "완료");
        when(jobs.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        service.fail(10L, "LATE_FAILURE", "늦게 도착한 실패입니다.");

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getResultCode()).isEqualTo("SUCCESS");
    }

    @Test
    void rejectsMissingLinkedJob() {
        when(jobs.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.fail(10L, "TASK_FAILED", "실패했습니다."))
                .withMessage("연결된 Job을 찾을 수 없습니다. jobId=10");
    }

    private OperationJob runningJob() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "user", "key", now);
        ReflectionTestUtils.setField(job, "id", 10L);
        job.start("worker", now.minusMinutes(1), now.plusMinutes(1));
        return job;
    }
}
