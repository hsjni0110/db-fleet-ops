package com.dbfleetops.operation.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.*;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationTaskService {

    private final AgentRepository agentRepository;
    private final OperationTaskRepository taskRepository;

    public OperationTaskService(AgentRepository agentRepository,
            OperationTaskRepository taskRepository) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public OperationTaskResponse createTask(CreateOperationTaskRequest request) {
        Agent agent = agentRepository.findById(request.agentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent not found. agentId=" + request.agentId()));

        OperationTask task;

        if (request.operationJobId() == null) {
            task = OperationTask.create(agent.getId(), request.taskType(),
                    request.parametersJson());
        } else {
            task = OperationTask.createForJob(agent.getId(), request.operationJobId(),
                    request.taskType(), request.parametersJson());
        }

        OperationTask savedTask = taskRepository.save(task);

        return OperationTaskResponse.from(savedTask);
    }

    @Transactional(readOnly = true)
    public NextOperationTaskResponse nextTask(Long agentId, String agentToken) {
        getAgentAndValidateToken(agentId, agentToken);

        return taskRepository
                .findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(agentId, OperationTaskStatus.QUEUED)
                .stream().findFirst().map(NextOperationTaskResponse::from)
                .orElseGet(NextOperationTaskResponse::empty);
    }

    @Transactional
    public OperationTaskResponse startTask(Long agentId, Long taskId,
            StartOperationTaskRequest request) {
        getAgentAndValidateToken(agentId, request.agentToken());

        OperationTask task = getTaskOwnedByAgent(agentId, taskId);

        task.start();

        return OperationTaskResponse.from(task);
    }

    @Transactional
    public OperationTaskResponse completeTask(Long agentId, Long taskId,
            CompleteOperationTaskRequest request) {
        getAgentAndValidateToken(agentId, request.agentToken());

        OperationTask task = getTaskOwnedByAgent(agentId, taskId);

        task.complete(request.resultPayloadJson());

        return OperationTaskResponse.from(task);
    }

    @Transactional
    public OperationTaskResponse failTask(Long agentId, Long taskId,
            FailOperationTaskRequest request) {
        getAgentAndValidateToken(agentId, request.agentToken());

        OperationTask task = getTaskOwnedByAgent(agentId, taskId);

        task.fail(request.errorCode(), request.errorMessage());

        return OperationTaskResponse.from(task);
    }

    private Agent getAgentAndValidateToken(Long agentId, String agentToken) {
        Agent agent = agentRepository.findById(agentId).orElseThrow(
                () -> new IllegalArgumentException("Agent not found. agentId=" + agentId));

        if (!agent.tokenMatches(agentToken)) {
            throw new IllegalArgumentException("Invalid agent token. agentId=" + agentId);
        }

        return agent;
    }

    private OperationTask getTaskOwnedByAgent(Long agentId, Long taskId) {
        OperationTask task = taskRepository.findById(taskId).orElseThrow(
                () -> new IllegalArgumentException("Operation task not found. taskId=" + taskId));

        if (!agentId.equals(task.getAgentId())) {
            throw new IllegalStateException("Task does not belong to agent. agentId=" + agentId
                    + ", taskAgentId=" + task.getAgentId());
        }

        return task;
    }

    @Transactional
    public OperationTaskResponse createBackupTaskForOperationJob(Long operationJobId,
            Long databaseId) {
        Agent agent =
                agentRepository.findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus.ONLINE)
                        .orElseThrow(() -> new IllegalStateException(
                                "No ONLINE agent available for backup task."));

        String parametersJson = """
                {
                  "operationJobId": %d,
                  "databaseId": %d,
                  "backupType": "LOGICAL",
                  "compression": true
                }
                """.formatted(operationJobId, databaseId);

        OperationTask task = OperationTask.createForJob(agent.getId(), operationJobId,
                OperationTaskType.MYSQL_LOGICAL_BACKUP, parametersJson);

        OperationTask savedTask = taskRepository.save(task);

        return OperationTaskResponse.from(savedTask);
    }
}
