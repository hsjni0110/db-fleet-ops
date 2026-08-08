package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.TaskClaim;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.dto.NextOperationTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class TaskClaimService implements TaskClaim {
    private final AgentReader agentRepository;
    private final TaskStore taskRepository;
    private final OperationTaskLeaseProperties properties;
    private final AuditWriter auditRecorder;
    private final Clock clock;

    public TaskClaimService(AgentReader agentRepository,
            TaskStore taskRepository, OperationTaskLeaseProperties properties,
            AuditWriter auditRecorder, Clock clock) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public NextOperationTaskResponse claimNext(Long agentId, String agentToken) {
        validateAgentToken(agentId, agentToken);
        var task = taskRepository.findNextForUpdate(
                agentId, OperationTaskStatus.QUEUED);
        if (task.isEmpty()) return NextOperationTaskResponse.empty();

        LocalDateTime now = LocalDateTime.now(clock);
        task.get().claim(now, now.plus(properties.duration()));
        auditRecorder.record("agent-" + agentId, "OPERATION_TASK_CLAIMED", "OPERATION_TASK",
                String.valueOf(task.get().getId()), "SUCCESS",
                "Task claimed. executionAttempt=" + task.get().getExecutionAttempt());
        return NextOperationTaskResponse.from(task.get());
    }

    private void validateAgentToken(Long agentId, String agentToken) {
        agentRepository.findAgent(agentId).orElseThrow(
                () -> new IllegalArgumentException("Agent not found. agentId=" + agentId));
        if (!agentRepository.matchesToken(agentId, agentToken)) {
            throw new IllegalArgumentException("Invalid agent token. agentId=" + agentId);
        }
    }
}
