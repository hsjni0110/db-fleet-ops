package com.dbfleetops.operation.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.audit.port.AuditRecorderPort;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.dto.NextOperationTaskResponse;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class OperationTaskClaimService {
    private final AgentRepository agentRepository;
    private final OperationTaskRepository taskRepository;
    private final OperationTaskLeaseProperties properties;
    private final AuditRecorderPort auditRecorder;
    private final Clock clock;

    public OperationTaskClaimService(AgentRepository agentRepository,
            OperationTaskRepository taskRepository, OperationTaskLeaseProperties properties,
            AuditRecorderPort auditRecorder, Clock clock) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public NextOperationTaskResponse claimNext(Long agentId, String agentToken) {
        validateAgentToken(agentId, agentToken);
        var task = taskRepository.findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(
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
        Agent agent = agentRepository.findById(agentId).orElseThrow(
                () -> new IllegalArgumentException("Agent not found. agentId=" + agentId));
        if (!agent.matchesToken(agentToken)) {
            throw new IllegalArgumentException("Invalid agent token. agentId=" + agentId);
        }
    }
}
