package com.dbfleetops.operation.task.application.service;


import com.dbfleetops.operation.shared.application.required.AgentExecutionTarget;
import com.dbfleetops.operation.shared.application.required.AgentReader;
import com.dbfleetops.operation.task.application.required.TaskStore;
import com.dbfleetops.operation.task.application.required.LinkedJobProgress;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import com.dbfleetops.operation.task.domain.TaskExecutionConflictException;
import com.dbfleetops.operation.task.dto.CompleteOperationTaskRequest;
import com.dbfleetops.operation.task.dto.FailOperationTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    private static final String REPORT_ID = "8d77288c-cf64-4ae8-a5be-a4010192fc6e";

    private AgentReader agents;
    private TaskStore tasks;
    private TaskSuccessDispatcher successDispatcher;
    private LinkedJobProgress jobs;
    private TaskReportService service;

    @BeforeEach
    void setUp() {
        agents = mock(AgentReader.class);
        tasks = mock(TaskStore.class);
        successDispatcher = mock(TaskSuccessDispatcher.class);
        jobs = mock(LinkedJobProgress.class);

        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "token")).thenReturn(true);

        service = new TaskReportService(agents, tasks, successDispatcher, jobs, CLOCK,
                new OperationTaskResultFingerprint());
    }

    @Test
    void duplicateFailureDoesNotFailLinkedJobAgain() {
        OperationTask task = runningLinkedTask(1L, 100L);
        when(tasks.findById(10L)).thenReturn(Optional.of(task));
        FailOperationTaskRequest request = new FailOperationTaskRequest(
                "token", 1, "BACKUP_FAILED", REPORT_ID, "백업 실패");

        service.failTask(1L, 10L, request);
        service.failTask(1L, 10L, request);

        verify(jobs, times(1)).fail(100L, "BACKUP_FAILED", "백업 실패");
    }

    @Test
    void successResultIsDispatchedOnlyOnce() {
        OperationTask task = runningTask(1L);
        when(tasks.findById(10L)).thenReturn(Optional.of(task));
        CompleteOperationTaskRequest request = new CompleteOperationTaskRequest(
                "token", 1, REPORT_ID, "{}");

        service.completeTask(1L, 10L, request);
        service.completeTask(1L, 10L, request);

        verify(successDispatcher, times(1)).dispatch(task, "{}");
    }

    @Test
    void anotherAgentCannotReportTaskResult() {
        OperationTask task = runningTask(2L);
        when(tasks.findById(10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.completeTask(1L, 10L,
                new CompleteOperationTaskRequest("token", 1, REPORT_ID, "{}")))
                .isInstanceOf(TaskExecutionConflictException.class)
                .hasMessageContaining("다른 Agent");
    }

    private OperationTask runningTask(Long agentId) {
        OperationTask task = OperationTask.create(
                agentId, OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        return claim(task);
    }

    private OperationTask runningLinkedTask(Long agentId, Long jobId) {
        OperationTask task = OperationTask.createForJob(
                agentId, jobId, OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}");
        return claim(task);
    }

    private OperationTask claim(OperationTask task) {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        task.claim(now.minusSeconds(1), now.plusMinutes(1));
        return task;
    }
}
