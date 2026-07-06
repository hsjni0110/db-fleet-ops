package com.dbfleetops.operation.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.infra.AgentHostMetricRepository;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.dto.CompleteOperationTaskRequest;
import com.dbfleetops.operation.dto.FailOperationTaskRequest;
import com.dbfleetops.operation.infra.OperationJobRepository;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupJobOperationTaskFlowTest {

    @Mock
    private OperationJobRepository jobRepository;

    @Mock
    private OperationTaskRepository taskRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AuditRecorderPort auditRecorderPort;

    @Mock
    private AgentHostMetricRepository agentHostMetricRepository;

    @Test
    void backupJobClaimCreatesTaskAndTaskCompleteSucceedsJob() {
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

        ReflectionTestUtils.setField(job, "id", 100L);

        Agent agent = newAgent();

        ReflectionTestUtils.setField(agent, "id", 1L);

        AtomicReference<OperationTask> savedTask = new AtomicReference<>();

        when(jobRepository
                .findTop10ByStatusAndAvailableAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                        eq(JobStatus.QUEUED), any(LocalDateTime.class))).thenReturn(List.of(job));

        when(agentRepository.findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE))
                .thenReturn(Optional.of(agent));

        when(taskRepository.save(any(OperationTask.class))).thenAnswer(invocation -> {
            OperationTask task = invocation.getArgument(0);

            savedTask.set(task);

            return task;
        });

        OperationTaskService taskService = new OperationTaskService(agentRepository, taskRepository,
                jobRepository, agentHostMetricRepository);

        OperationWorkerService workerService =
                new OperationWorkerService(jobRepository, auditRecorderPort, taskService);

        ClaimJobResponse claimResponse = workerService.claimJob("worker-1");

        assertThat(claimResponse.claimed()).isTrue();

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);

        OperationTask task = savedTask.get();

        assertThat(task).isNotNull();

        assertThat(task.getTaskType()).isEqualTo(OperationTaskType.MYSQL_LOGICAL_BACKUP);

        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.QUEUED);

        assertThat(task.getOperationJobId()).isEqualTo(job.getId());

        task.start();

        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        taskService.completeTask(1L, 10L,
                new CompleteOperationTaskRequest("agent-token-001", "{\"status\":\"CREATED\"}"));

        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);

        assertThat(job.getResultCode()).isEqualTo("SUCCESS");

        assertThat(job.getResultMessage()).isEqualTo("{\"status\":\"CREATED\"}");
    }

    @Test
    void backupTaskFailFailsLinkedJob() {
        OperationJob job = OperationJob.create(JobType.BACKUP, 1L, "local-user", "idem-001");

        job.start("worker-1", LocalDateTime.now().plusSeconds(60));

        Agent agent = newAgent();

        OperationTask task =
                OperationTask.createForJob(1L, 100L, OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");

        task.start();

        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

        OperationTaskService taskService = new OperationTaskService(agentRepository, taskRepository,
                jobRepository, agentHostMetricRepository);

        taskService.failTask(1L, 10L, new FailOperationTaskRequest("agent-token-001",
                "BACKUP_FAILED", "mysqldump failed"));

        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.FAILED);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);

        assertThat(job.getResultCode()).isEqualTo("BACKUP_FAILED");

        assertThat(job.getResultMessage()).isEqualTo("mysqldump failed");
    }

    private Agent newAgent() {
        return Agent.register("local-agent", "localhost", "127.0.0.1", "Linux", "0.1.0",
                "agent-token-001");
    }
}
