package com.dbfleetops.operation.task.application.service;

import com.dbfleetops.operation.task.application.provided.TaskReports;
import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.application.required.LinkedJobProgress;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.ResultReportAcceptance;
import com.dbfleetops.operation.task.domain.TaskExecutionConflictException;
import com.dbfleetops.operation.task.dto.CompleteOperationTaskRequest;
import com.dbfleetops.operation.task.dto.FailOperationTaskRequest;
import com.dbfleetops.operation.task.dto.OperationTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.dbfleetops.operation.task.domain.ResultReportAcceptance.ACCEPTED;
import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;

/** Agent가 보낸 Task 결과를 한 번만 반영하고 후속 작업으로 전달합니다. */
@Service
public class TaskReportService implements TaskReports {

    private final AgentReader agents;
    private final TaskStore tasks;
    private final TaskResultDispatcher results;
    private final LinkedJobProgress jobs;
    private final Clock clock;
    private final OperationTaskResultFingerprint fingerprints;

    public TaskReportService(AgentReader agents, TaskStore tasks, TaskResultDispatcher results,
            LinkedJobProgress jobs, Clock clock,
            OperationTaskResultFingerprint fingerprints) {
        this.agents = agents;
        this.tasks = tasks;
        this.results = results;
        this.jobs = jobs;
        this.clock = clock;
        this.fingerprints = fingerprints;
    }

    @Override
    @Transactional
    public OperationTaskResponse completeTask(Long agentId, Long taskId,
            CompleteOperationTaskRequest request) {
        validateSuccessRequest(agentId, taskId, request);
        authenticateAgent(agentId, request.agentToken());

        OperationTask task = requireOwnedTask(agentId, taskId);
        ResultReportAcceptance acceptance = acceptSuccess(task, request);

        dispatchAcceptedResult(task, request.resultPayloadJson(), acceptance);

        return OperationTaskResponse.from(task);
    }

    @Override
    @Transactional
    public OperationTaskResponse failTask(Long agentId, Long taskId,
            FailOperationTaskRequest request) {
        validateFailureRequest(agentId, taskId, request);
        authenticateAgent(agentId, request.agentToken());

        OperationTask task = requireOwnedTask(agentId, taskId);
        ResultReportAcceptance acceptance = acceptFailure(task, request);

        failLinkedJob(task, request, acceptance);

        return OperationTaskResponse.from(task);
    }

    private ResultReportAcceptance acceptSuccess(OperationTask task,
            CompleteOperationTaskRequest request) {
        String fingerprint = fingerprints.success(request.resultPayloadJson());

        return task.acceptSuccessReport(
                request.executionAttempt(),
                request.resultReportId(),
                fingerprint,
                request.resultPayloadJson(),
                now());
    }

    private ResultReportAcceptance acceptFailure(OperationTask task,
            FailOperationTaskRequest request) {
        String fingerprint = fingerprints.failure(request.errorCode(), request.errorMessage());

        return task.acceptFailureReport(
                request.executionAttempt(),
                request.resultReportId(),
                fingerprint,
                request.errorCode(),
                request.errorMessage(),
                now());
    }

    private void dispatchAcceptedResult(OperationTask task, String resultPayload,
            ResultReportAcceptance acceptance) {
        if (acceptance == ACCEPTED) {
            results.dispatch(task, resultPayload);
        }
    }

    private void failLinkedJob(OperationTask task, FailOperationTaskRequest request,
            ResultReportAcceptance acceptance) {
        if (acceptance == ACCEPTED) {
            jobs.fail(task.getOperationJobId(), request.errorCode(), request.errorMessage());
        }
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

    private void validateSuccessRequest(Long agentId, Long taskId,
            CompleteOperationTaskRequest request) {
        validateTarget(agentId, taskId);
        notNull(request, "Task 성공 결과는 필수입니다.");
        hasText(request.agentToken(), "Agent Token은 필수입니다.");
        hasText(request.resultReportId(), "결과 보고 ID는 필수입니다.");
    }

    private void validateFailureRequest(Long agentId, Long taskId,
            FailOperationTaskRequest request) {
        validateTarget(agentId, taskId);
        notNull(request, "Task 실패 결과는 필수입니다.");
        hasText(request.agentToken(), "Agent Token은 필수입니다.");
        hasText(request.resultReportId(), "결과 보고 ID는 필수입니다.");
        hasText(request.errorCode(), "오류 코드는 필수입니다.");
    }

    private void validateTarget(Long agentId, Long taskId) {
        notNull(agentId, "Agent ID는 필수입니다.");
        notNull(taskId, "Task ID는 필수입니다.");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
