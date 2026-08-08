package com.dbfleetops.operation.task.application.service;

import com.dbfleetops.operation.task.application.provided.TaskLease;
import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.TaskExecutionConflictException;
import com.dbfleetops.operation.task.dto.OperationTaskLeaseResponse;
import com.dbfleetops.operation.task.dto.RenewOperationTaskLeaseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;

@Service
public class TaskLeaseService implements TaskLease {

    private final AgentReader agents;
    private final TaskStore tasks;
    private final OperationTaskLeaseProperties lease;
    private final Clock clock;

    public TaskLeaseService(AgentReader agents, TaskStore tasks,
            OperationTaskLeaseProperties lease, Clock clock) {
        this.agents = agents;
        this.tasks = tasks;
        this.lease = lease;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OperationTaskLeaseResponse renew(Long agentId, Long taskId,
            RenewOperationTaskLeaseRequest request) {
        validateRequest(agentId, taskId, request);
        authenticateAgent(agentId, request.agentToken());

        OperationTask task = requireOwnedTask(agentId, taskId);

        renewTaskLease(task, request.executionAttempt());

        return OperationTaskLeaseResponse.from(task);
    }

    private void renewTaskLease(OperationTask task, int executionAttempt) {
        LocalDateTime renewedAt = now();
        task.renewLease(executionAttempt, renewedAt, renewedAt.plus(lease.duration()));
    }

    private OperationTask requireOwnedTask(Long agentId, Long taskId) {
        OperationTask task = tasks.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task를 찾을 수 없습니다. taskId=" + taskId));

        if (!agentId.equals(task.getAgentId())) {
            throw new TaskExecutionConflictException(
                    "다른 Agent에게 배정된 Task입니다. agentId=" + agentId
                            + ", taskAgentId=" + task.getAgentId());
        }

        return task;
    }

    private void authenticateAgent(Long agentId, String agentToken) {
        agents.findAgent(agentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent를 찾을 수 없습니다. agentId=" + agentId));

        if (!agents.matchesToken(agentId, agentToken)) {
            throw new IllegalArgumentException("Agent Token이 올바르지 않습니다. agentId=" + agentId);
        }
    }

    private void validateRequest(Long agentId, Long taskId,
            RenewOperationTaskLeaseRequest request) {
        notNull(agentId, "Agent ID는 필수입니다.");
        notNull(taskId, "Task ID는 필수입니다.");
        notNull(request, "Task 실행권 갱신 요청은 필수입니다.");
        hasText(request.agentToken(), "Agent Token은 필수입니다.");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
