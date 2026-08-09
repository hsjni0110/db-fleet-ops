package com.dbfleetops.operation.task.application.service;

import com.dbfleetops.operation.workflow.application.LinkedJobFailureService;

import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.shared.application.required.AgentExecutionTarget;
import com.dbfleetops.operation.shared.application.required.AuditWriter;
import com.dbfleetops.operation.job.domain.JobStatus;
import com.dbfleetops.operation.job.domain.JobType;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskStatus;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import com.dbfleetops.operation.task.dto.RenewOperationTaskLeaseRequest;
import com.dbfleetops.operation.task.domain.TaskExecutionConflictException;
import com.dbfleetops.operation.job.application.required.JobStore;
import com.dbfleetops.operation.task.application.required.TaskStore;
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

class TaskLeaseServicesTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-07T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 3, 0);
    private static final OperationTaskLeaseProperties PROPERTIES =
            new OperationTaskLeaseProperties(Duration.ofSeconds(60), Duration.ofSeconds(20),
                    Duration.ofSeconds(5), 3, 100);

    @Test
    void claimNextAtomicallyStartsTaskAndReturnsExecutionIdentity() {
        AgentReader agents = mock(AgentReader.class);
        TaskStore tasks = mock(TaskStore.class);
        AuditWriter audit = mock(AuditWriter.class);
        OperationTask task = OperationTask.create(1L,
                OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "agent-token-001")).thenReturn(true);
        when(tasks.findNextForUpdate(
                1L, OperationTaskStatus.QUEUED)).thenReturn(Optional.of(task));

        var response = new TaskClaimService(agents, tasks, PROPERTIES, audit, CLOCK)
                .claimNext(1L, "agent-token-001");

        assertThat(response.executionAttempt()).isEqualTo(1);
        assertThat(response.leaseExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.RUNNING);
    }

    @Test
    void claimNextReturnsEmptyWhenAgentHasNoWaitingTask() {
        AgentReader agents = mock(AgentReader.class);
        TaskStore tasks = mock(TaskStore.class);
        AuditWriter audit = mock(AuditWriter.class);
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "agent-token-001")).thenReturn(true);
        when(tasks.findNextForUpdate(1L, OperationTaskStatus.QUEUED))
                .thenReturn(Optional.empty());

        var response = new TaskClaimService(agents, tasks, PROPERTIES, audit, CLOCK)
                .claimNext(1L, "agent-token-001");

        assertThat(response.taskId()).isNull();
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void renewExtendsLeaseAndRecordsProgressTime() {
        AgentReader agents = mock(AgentReader.class);
        TaskStore tasks = mock(TaskStore.class);
        OperationTask task = OperationTask.create(1L,
                OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        task.claim(NOW.minusSeconds(10), NOW.plusSeconds(50));
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "agent-token-001")).thenReturn(true);
        when(tasks.findById(10L)).thenReturn(Optional.of(task));

        var response = new TaskLeaseService(agents, tasks, PROPERTIES, CLOCK)
                .renew(1L, 10L, new RenewOperationTaskLeaseRequest("agent-token-001", 1));

        assertThat(response.leaseExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(response.lastProgressAt()).isEqualTo(NOW);
    }

    @Test
    void staleAttemptCannotRenewLease() {
        AgentReader agents = mock(AgentReader.class);
        TaskStore tasks = mock(TaskStore.class);
        OperationTask task = OperationTask.create(1L,
                OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        task.claim(NOW.minusSeconds(10), NOW.plusSeconds(50));
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "agent-token-001")).thenReturn(true);
        when(tasks.findById(10L)).thenReturn(Optional.of(task));

        TaskLeaseService service =
                new TaskLeaseService(agents, tasks, PROPERTIES, CLOCK);

        assertThatThrownBy(() -> service.renew(1L, 10L,
                new RenewOperationTaskLeaseRequest("agent-token-001", 2)))
                .isInstanceOf(TaskExecutionConflictException.class);
    }

    @Test
    void finalExpiredAttemptTimesOutTaskAndLinkedJob() {
        TaskStore tasks = mock(TaskStore.class);
        JobStore jobs = mock(JobStore.class);
        AuditWriter audit = mock(AuditWriter.class);
        OperationTask task = OperationTask.createForJob(1L, 100L,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        for (int attempt = 1; attempt <= 3; attempt++) {
            LocalDateTime claimedAt = NOW.minusMinutes(4 - attempt);
            task.claim(claimedAt, claimedAt.plusSeconds(30));
            if (attempt < 3) task.requeueExpiredLease(claimedAt.plusSeconds(30), 3);
        }
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "user", "key", NOW);
        job.start("worker-1", NOW, NOW.plusMinutes(1));
        when(tasks.findExpiredForUpdate(eq(OperationTaskStatus.RUNNING), eq(NOW),
                eq(100))).thenReturn(List.of(task));
        when(jobs.findByIdForUpdate(100L)).thenReturn(Optional.of(job));

        int recovered = new ExpiredTaskService(tasks, PROPERTIES, audit, CLOCK,
                new LinkedJobFailureService(jobs, CLOCK))
                .recoverExpiredTasks();

        assertThat(recovered).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.TIMED_OUT);
        assertThat(job.getStatus()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(job.getResultCode()).isEqualTo("TASK_LEASE_EXPIRED");
    }

}
