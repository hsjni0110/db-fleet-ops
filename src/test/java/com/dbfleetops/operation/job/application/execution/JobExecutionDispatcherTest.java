package com.dbfleetops.operation.job.application.execution;


import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobExecutionDispatcherTest {

    @Test
    void usesExecutionThatSupportsJobType() {
        JobExecution backup = mock(JobExecution.class);
        OperationJob job = OperationJob.create(
                JobType.BACKUP, 1L, "user", "key", LocalDateTime.of(2026, 8, 8, 0, 0));
        when(backup.supports(JobType.BACKUP)).thenReturn(true);
        when(backup.execute(job)).thenReturn(JobExecutionOutcome.inProgress("Task 생성"));

        JobExecutionOutcome outcome = new JobExecutionDispatcher(List.of(backup)).execute(job);

        assertThat(outcome.status()).isEqualTo(JobExecutionStatus.IN_PROGRESS);
    }

    @Test
    void rejectsJobWithoutExecution() {
        OperationJob job = OperationJob.create(
                JobType.BACKUP, 1L, "user", "key", LocalDateTime.of(2026, 8, 8, 0, 0));

        assertThatThrownBy(() -> new JobExecutionDispatcher(List.of()).execute(job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Job 실행 방법");
    }
}
