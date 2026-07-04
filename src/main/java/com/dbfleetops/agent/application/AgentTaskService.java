package com.dbfleetops.agent.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentTask;
import com.dbfleetops.agent.domain.AgentTaskStatus;
import com.dbfleetops.agent.dto.AgentTaskResponse;
import com.dbfleetops.agent.dto.CompleteAgentTaskRequest;
import com.dbfleetops.agent.dto.CreateAgentTaskRequest;
import com.dbfleetops.agent.dto.FailAgentTaskRequest;
import com.dbfleetops.agent.dto.NextAgentTaskResponse;
import com.dbfleetops.agent.dto.StartAgentTaskRequest;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.agent.infra.AgentTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTaskService {

    private final AgentRepository agentRepository;
    private final AgentTaskRepository taskRepository;

    public AgentTaskService(
            AgentRepository agentRepository,
            AgentTaskRepository taskRepository
    ) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public AgentTaskResponse createTask(
            CreateAgentTaskRequest request
    ) {
        Agent agent =
                agentRepository.findById(request.agentId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Agent not found. agentId=" + request.agentId()
                        ));

        AgentTask task =
                AgentTask.create(
                        request.agentId(),
                        request.taskType(),
                        request.parametersJson()
                );

        AgentTask savedTask =
                taskRepository.save(task);

        return AgentTaskResponse.from(savedTask);
    }

    @Transactional(readOnly = true)
    public NextAgentTaskResponse nextTask(
            Long agentId,
            String agentToken
    ) {
        getAgentAndValidateToken(
                agentId,
                agentToken
        );

        return taskRepository
                .findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(
                        agentId,
                        AgentTaskStatus.QUEUED
                )
                .stream()
                .findFirst()
                .map(NextAgentTaskResponse::from)
                .orElseGet(NextAgentTaskResponse::empty);
    }

    @Transactional
    public AgentTaskResponse startTask(
            Long agentId,
            Long taskId,
            StartAgentTaskRequest request
    ) {
        getAgentAndValidateToken(
                agentId,
                request.agentToken()
        );

        AgentTask task =
                getTaskOwnedByAgent(
                        agentId,
                        taskId
                );

        task.start();

        return AgentTaskResponse.from(task);
    }

    @Transactional
    public AgentTaskResponse completeTask(
            Long agentId,
            Long taskId,
            CompleteAgentTaskRequest request
    ) {
        getAgentAndValidateToken(
                agentId,
                request.agentToken()
        );

        AgentTask task =
                getTaskOwnedByAgent(
                        agentId,
                        taskId
                );

        task.complete(
                request.resultPayloadJson()
        );

        return AgentTaskResponse.from(task);
    }

    @Transactional
    public AgentTaskResponse failTask(
            Long agentId,
            Long taskId,
            FailAgentTaskRequest request
    ) {
        getAgentAndValidateToken(
                agentId,
                request.agentToken()
        );

        AgentTask task =
                getTaskOwnedByAgent(
                        agentId,
                        taskId
                );

        task.fail(
                request.errorCode(),
                request.errorMessage()
        );

        return AgentTaskResponse.from(task);
    }

    private Agent getAgentAndValidateToken(
            Long agentId,
            String agentToken
    ) {
        Agent agent =
                agentRepository.findById(agentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Agent not found. agentId=" + agentId
                        ));

        if (!agent.tokenMatches(agentToken)) {
            throw new IllegalArgumentException(
                    "Invalid agent token. agentId=" + agentId
            );
        }

        return agent;
    }

    private AgentTask getTaskOwnedByAgent(
            Long agentId,
            Long taskId
    ) {
        AgentTask task =
                taskRepository.findById(taskId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Agent task not found. taskId=" + taskId
                        ));

        if (!agentId.equals(task.getAgentId())) {
            throw new IllegalStateException(
                    "Task does not belong to agent. agentId="
                            + agentId
                            + ", taskAgentId="
                            + task.getAgentId()
            );
        }

        return task;
    }
}