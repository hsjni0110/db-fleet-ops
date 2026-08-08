package com.dbfleetops.operation.task.application.service;

import com.dbfleetops.operation.task.application.provided.TaskClaim;
import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.shared.application.required.AuditWriter;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskStatus;
import com.dbfleetops.operation.task.dto.NextOperationTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;

@Service
public class TaskClaimService implements TaskClaim {

    private final AgentReader agents;
    private final TaskStore tasks;
    private final OperationTaskLeaseProperties lease;
    private final AuditWriter audit;
    private final Clock clock;

    public TaskClaimService(AgentReader agents, TaskStore tasks,
            OperationTaskLeaseProperties lease, AuditWriter audit, Clock clock) {
        this.agents = agents;
        this.tasks = tasks;
        this.lease = lease;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NextOperationTaskResponse claimNext(Long agentId, String agentToken) {
        validateRequest(agentId, agentToken);
        authenticateAgent(agentId, agentToken);

        Optional<OperationTask> waitingTask = findWaitingTask(agentId);

        if (waitingTask.isEmpty()) {
            return NextOperationTaskResponse.empty();
        }

        OperationTask task = waitingTask.get();

        claimTask(task);
        recordClaim(agentId, task);

        return NextOperationTaskResponse.from(task);
    }

    private Optional<OperationTask> findWaitingTask(Long agentId) {
        return tasks.findNextForUpdate(agentId, OperationTaskStatus.QUEUED);
    }

    private void claimTask(OperationTask task) {
        LocalDateTime claimedAt = now();
        task.claim(claimedAt, claimedAt.plus(lease.duration()));
    }

    private void recordClaim(Long agentId, OperationTask task) {
        audit.record("agent-" + agentId, "OPERATION_TASK_CLAIMED", "OPERATION_TASK",
                String.valueOf(task.getId()), "SUCCESS",
                "Task claimed. executionAttempt=" + task.getExecutionAttempt());
    }

    private void authenticateAgent(Long agentId, String agentToken) {
        agents.findAgent(agentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent를 찾을 수 없습니다. agentId=" + agentId));

        if (!agents.matchesToken(agentId, agentToken)) {
            throw new IllegalArgumentException("Agent Token이 올바르지 않습니다. agentId=" + agentId);
        }
    }

    private void validateRequest(Long agentId, String agentToken) {
        notNull(agentId, "Agent ID는 필수입니다.");
        hasText(agentToken, "Agent Token은 필수입니다.");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
