package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.TaskLease;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.dto.OperationTaskLeaseResponse;
import com.dbfleetops.operation.dto.RenewOperationTaskLeaseRequest;
import com.dbfleetops.operation.exception.TaskExecutionConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class TaskLeaseService implements TaskLease {
    private final AgentReader agentRepository;
    private final TaskStore taskRepository;
    private final OperationTaskLeaseProperties properties;
    private final Clock clock;

    public TaskLeaseService(AgentReader agentRepository,
            TaskStore taskRepository, OperationTaskLeaseProperties properties,
            Clock clock) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public OperationTaskLeaseResponse renew(Long agentId, Long taskId,
            RenewOperationTaskLeaseRequest request) {
        validateAgentToken(agentId, request.agentToken());
        var task = taskRepository.findById(taskId).orElseThrow(
                () -> new IllegalArgumentException("Operation task not found. taskId=" + taskId));
        if (!agentId.equals(task.getAgentId())) {
            throw new TaskExecutionConflictException("Task does not belong to agent.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            task.renewLease(request.executionAttempt(), now, now.plus(properties.duration()));
        } catch (IllegalStateException exception) {
            throw new TaskExecutionConflictException(exception.getMessage(), exception);
        }
        return OperationTaskLeaseResponse.from(task);
    }

    private void validateAgentToken(Long agentId, String agentToken) {
        agentRepository.findAgent(agentId).orElseThrow(
                () -> new IllegalArgumentException("Agent not found. agentId=" + agentId));
        if (!agentRepository.matchesToken(agentId, agentToken)) {
            throw new IllegalArgumentException("Invalid agent token. agentId=" + agentId);
        }
    }
}
