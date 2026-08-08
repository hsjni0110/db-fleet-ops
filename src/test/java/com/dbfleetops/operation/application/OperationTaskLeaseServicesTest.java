package com.dbfleetops.operation.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.RenewOperationTaskLeaseRequest;
import com.dbfleetops.operation.exception.TaskExecutionConflictException;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

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
import static org.mockito.Mockito.when;

class OperationTaskLeaseServicesTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-07T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 3, 0);
    private static final OperationTaskLeaseProperties PROPERTIES =
            new OperationTaskLeaseProperties(Duration.ofSeconds(60), Duration.ofSeconds(20),
                    Duration.ofSeconds(5), 3, 100);

    @Test
    void claimNextAtomicallyStartsTaskAndReturnsExecutionIdentity() {
        AgentRepository agents = mock(AgentRepository.class);
        OperationTaskRepository tasks = mock(OperationTaskRepository.class);
        AuditRecorderPort audit = mock(AuditRecorderPort.class);
        OperationTask task = OperationTask.create(1L,
                OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        when(agents.findById(1L)).thenReturn(Optional.of(agent()));
        when(tasks.findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(
                1L, OperationTaskStatus.QUEUED)).thenReturn(Optional.of(task));

        var response = new OperationTaskClaimService(agents, tasks, PROPERTIES, audit, CLOCK)
                .claimNext(1L, "agent-token-001");

        assertThat(response.executionAttempt()).isEqualTo(1);
        assertThat(response.leaseExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.RUNNING);
    }

    @Test
    void staleAttemptCannotRenewLease() {
        AgentRepository agents = mock(AgentRepository.class);
        OperationTaskRepository tasks = mock(OperationTaskRepository.class);
        OperationTask task = OperationTask.create(1L,
                OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        task.claim(NOW.minusSeconds(10), NOW.plusSeconds(50));
        when(agents.findById(1L)).thenReturn(Optional.of(agent()));
        when(tasks.findById(10L)).thenReturn(Optional.of(task));

        OperationTaskLeaseService service =
                new OperationTaskLeaseService(agents, tasks, PROPERTIES, CLOCK);

        assertThatThrownBy(() -> service.renew(1L, 10L,
                new RenewOperationTaskLeaseRequest("agent-token-001", 2)))
                .isInstanceOf(TaskExecutionConflictException.class);
    }

    @Test
    void finalExpiredAttemptTimesOutTaskAndLinkedJob() {
        OperationTaskRepository tasks = mock(OperationTaskRepository.class);
        OperationJobRepository jobs = mock(OperationJobRepository.class);
        AuditRecorderPort audit = mock(AuditRecorderPort.class);
        OperationTask task = OperationTask.createForJob(1L, 100L,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        for (int attempt = 1; attempt <= 3; attempt++) {
            LocalDateTime claimedAt = NOW.minusMinutes(4 - attempt);
            task.claim(claimedAt, claimedAt.plusSeconds(30));
            if (attempt < 3) task.requeueExpiredLease(claimedAt.plusSeconds(30), 3);
        }
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "user", "key");
        job.start("worker-1", NOW.plusMinutes(1));
        when(tasks.findExpiredForUpdate(eq(OperationTaskStatus.RUNNING), eq(NOW),
                any(Pageable.class))).thenReturn(List.of(task));
        when(jobs.findById(100L)).thenReturn(Optional.of(job));

        int recovered = new ExpiredOperationTaskService(tasks, jobs, PROPERTIES, audit, CLOCK)
                .recoverExpiredTasks();

        assertThat(recovered).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.TIMED_OUT);
        assertThat(job.getStatus()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(job.getResultCode()).isEqualTo("TASK_LEASE_EXPIRED");
    }

    private Agent agent() {
        return Agent.register("agent", "host", "127.0.0.1", "linux", "amd64", "1.0",
                "agent-token-001");
    }
}
