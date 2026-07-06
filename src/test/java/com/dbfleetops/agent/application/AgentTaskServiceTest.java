package com.dbfleetops.agent.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.domain.AgentTask;
import com.dbfleetops.agent.domain.AgentTaskStatus;
import com.dbfleetops.agent.domain.AgentTaskType;
import com.dbfleetops.agent.dto.AgentTaskResponse;
import com.dbfleetops.agent.dto.CompleteAgentTaskRequest;
import com.dbfleetops.agent.dto.CreateAgentTaskRequest;
import com.dbfleetops.agent.dto.FailAgentTaskRequest;
import com.dbfleetops.agent.dto.NextAgentTaskResponse;
import com.dbfleetops.agent.dto.StartAgentTaskRequest;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.agent.infra.AgentTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTaskServiceTest {

        @Mock
        private AgentRepository agentRepository;

        @Mock
        private AgentTaskRepository taskRepository;

        @Test
        void createTaskCreatesQueuedTask() {
                Agent agent = newAgent();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.save(any(AgentTask.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                AgentTaskService service = new AgentTaskService(agentRepository, taskRepository);

                var response = service.createTask(new CreateAgentTaskRequest(1L,
                                AgentTaskType.COLLECT_LINUX_STATUS, "{}"));

                assertThat(response.taskType()).isEqualTo(AgentTaskType.COLLECT_LINUX_STATUS);

                assertThat(response.status()).isEqualTo(AgentTaskStatus.QUEUED);
        }

        @Test
        void nextTaskReturnsQueuedTask() {
                Agent agent = newAgent();

                AgentTask task = AgentTask.create(1L, AgentTaskType.COLLECT_LINUX_STATUS, "{}");

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(1L,
                                AgentTaskStatus.QUEUED)).thenReturn(List.of(task));

                AgentTaskService service = new AgentTaskService(agentRepository, taskRepository);

                NextAgentTaskResponse response = service.nextTask(1L, "agent-token-001");

                assertThat(response.hasTask()).isTrue();

                assertThat(response.taskType()).isEqualTo(AgentTaskType.COLLECT_LINUX_STATUS);
        }

        @Test
        void startTaskChangesQueuedTaskToRunning() {
                Agent agent = newAgent();

                AgentTask task = AgentTask.create(1L, AgentTaskType.COLLECT_LINUX_STATUS, "{}");

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                AgentTaskService service = new AgentTaskService(agentRepository, taskRepository);

                var response = service.startTask(1L, 10L,
                                new StartAgentTaskRequest("agent-token-001"));

                assertThat(response.status()).isEqualTo(AgentTaskStatus.RUNNING);
        }

        @Test
        void completeTaskChangesRunningTaskToSucceeded() {
                Agent agent = newAgent();

                AgentTask task = AgentTask.create(1L, AgentTaskType.COLLECT_LINUX_STATUS, "{}");

                task.start();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                AgentTaskService service = new AgentTaskService(agentRepository, taskRepository);

                var response = service.completeTask(1L, 10L, new CompleteAgentTaskRequest(
                                "agent-token-001", "{\"cpuUsagePercent\":12.5}"));

                assertThat(response.status()).isEqualTo(AgentTaskStatus.SUCCEEDED);

                assertThat(response.resultPayloadJson()).isEqualTo("{\"cpuUsagePercent\":12.5}");
        }

        @Test
        void failTaskChangesRunningTaskToFailed() {
                Agent agent = newAgent();

                AgentTask task = AgentTask.create(1L, AgentTaskType.COLLECT_LINUX_STATUS, "{}");

                task.start();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

                AgentTaskService service = new AgentTaskService(agentRepository, taskRepository);

                var response = service.failTask(1L, 10L, new FailAgentTaskRequest("agent-token-001",
                                "LINUX_STATUS_FAILED", "failed to read /proc/stat"));

                assertThat(response.status()).isEqualTo(AgentTaskStatus.FAILED);

                assertThat(response.errorCode()).isEqualTo("LINUX_STATUS_FAILED");
        }

        @Test
        void nextTaskThrowsExceptionWhenTokenIsInvalid() {
                Agent agent = newAgent();

                when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

                AgentTaskService service = new AgentTaskService(agentRepository, taskRepository);

                assertThrows(IllegalArgumentException.class,
                                () -> service.nextTask(1L, "wrong-token"));
        }

        private Agent newAgent() {
                return Agent.register("local-agent", "localhost", "127.0.0.1", "Linux", "0.1.0",
                                "agent-token-001");
        }

        @Test
        void createBackupTaskForOperationJobCreatesMySQLBackupTaskOnOnlineAgent() {
                Agent agent = newAgent();

                when(agentRepository
                                .findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE))
                                                .thenReturn(Optional.of(agent));

                when(taskRepository.save(any(AgentTask.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                AgentTaskService service = new AgentTaskService(agentRepository, taskRepository);

                AgentTaskResponse response = service.createBackupTaskForOperationJob(100L, 1L);

                assertThat(response.operationJobId()).isEqualTo(100L);

                assertThat(response.taskType()).isEqualTo(AgentTaskType.MYSQL_LOGICAL_BACKUP);

                assertThat(response.status()).isEqualTo(AgentTaskStatus.QUEUED);

                assertThat(response.parametersJson()).contains("\"operationJobId\": 100",
                                "\"databaseId\": 1", "\"backupType\": \"LOGICAL\"");
        }

        @Test
        void createBackupTaskForOperationJobThrowsExceptionWhenNoOnlineAgentExists() {
                when(agentRepository
                                .findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE))
                                                .thenReturn(Optional.empty());

                AgentTaskService service = new AgentTaskService(agentRepository, taskRepository);

                assertThrows(IllegalStateException.class,
                                () -> service.createBackupTaskForOperationJob(100L, 1L));
        }
}
