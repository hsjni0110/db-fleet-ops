package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.provided.TaskReports;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.*;
import com.dbfleetops.operation.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/** Agent 결과 보고의 인증, 멱등 처리와 후속 처리를 담당합니다. */
@Service
public class TaskReportService implements TaskReports {
    private final AgentReader agents;
    private final TaskStore tasks;
    private final TaskResultDispatcher dispatcher;
    private final JobTaskCoordinator coordinator;
    private final Clock clock;
    private final OperationTaskResultFingerprint fingerprint;
    public TaskReportService(AgentReader agents, TaskStore tasks, TaskResultDispatcher dispatcher,
            JobTaskCoordinator coordinator, Clock clock, OperationTaskResultFingerprint fingerprint) {
        this.agents = agents; this.tasks = tasks; this.dispatcher = dispatcher;
        this.coordinator = coordinator; this.clock = clock; this.fingerprint = fingerprint;
    }
    @Transactional
    public OperationTaskResponse completeTask(Long agentId, Long taskId,
            CompleteOperationTaskRequest request) {
        validateAgent(agentId, request.agentToken());
        OperationTask task = ownedTask(agentId, taskId);
        ResultReportAcceptance accepted = task.acceptSuccessReport(
                request.executionAttempt(), request.resultReportId(),
                fingerprint.success(request.resultPayloadJson()), request.resultPayloadJson(),
                LocalDateTime.now(clock));
        if (accepted == ResultReportAcceptance.ACCEPTED) dispatcher.dispatch(task, request.resultPayloadJson());
        return OperationTaskResponse.from(task);
    }
    @Transactional
    public OperationTaskResponse failTask(Long agentId, Long taskId, FailOperationTaskRequest request) {
        validateAgent(agentId, request.agentToken());
        OperationTask task = ownedTask(agentId, taskId);
        ResultReportAcceptance accepted = task.acceptFailureReport(
                request.executionAttempt(), request.resultReportId(),
                fingerprint.failure(request.errorCode(), request.errorMessage()),
                request.errorCode(), request.errorMessage(), LocalDateTime.now(clock));
        if (accepted == ResultReportAcceptance.ACCEPTED)
            coordinator.failLinkedJob(task, request.errorCode(), request.errorMessage());
        return OperationTaskResponse.from(task);
    }
    private void validateAgent(Long id, String token) {
        agents.findAgent(id).orElseThrow(() -> new IllegalArgumentException("Agent not found. agentId=" + id));
        if (!agents.matchesToken(id, token)) throw new IllegalArgumentException("Invalid agent token. agentId=" + id);
    }
    private OperationTask ownedTask(Long agentId, Long taskId) {
        OperationTask task = tasks.findById(taskId).orElseThrow(() ->
                new IllegalArgumentException("Operation task not found. taskId=" + taskId));
        if (!agentId.equals(task.getAgentId())) throw new TaskExecutionConflictException(
                "Task does not belong to agent. agentId=" + agentId + ", taskAgentId=" + task.getAgentId());
        return task;
    }
}
