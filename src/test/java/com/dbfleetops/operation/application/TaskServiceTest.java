package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.*;
import com.dbfleetops.operation.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TaskServiceTest {
    @Test
    void createsStandaloneTask() {
        AgentReader agents = mock(AgentReader.class);
        TaskStore tasks = mock(TaskStore.class);
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(tasks.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = new TaskService(agents, tasks).createTask(new CreateOperationTaskRequest(
                1L, null, OperationTaskType.COLLECT_LINUX_STATUS, "{}"));
        assertThat(response.status()).isEqualTo(OperationTaskStatus.QUEUED);
    }

    @Test
    void duplicateSuccessDoesNotRunHandlerAgain() {
        AgentReader agents = mock(AgentReader.class);
        TaskStore tasks = mock(TaskStore.class);
        TaskResultHandler handler = mock(TaskResultHandler.class);
        JobStore jobs = mock(JobStore.class);
        when(agents.findAgent(1L)).thenReturn(Optional.of(new AgentExecutionTarget(1L, true)));
        when(agents.matchesToken(1L, "token")).thenReturn(true);
        OperationTask task = OperationTask.create(1L, OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        ReflectionTestUtils.setField(task, "id", 10L);
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 0, 0);
        task.claim(now, now.plusMinutes(1));
        when(tasks.findById(10L)).thenReturn(Optional.of(task));
        when(handler.supports(OperationTaskType.COLLECT_LINUX_STATUS)).thenReturn(true);
        TaskReportService service = new TaskReportService(agents, tasks,
                new TaskResultDispatcher(List.of(handler)), new JobTaskCoordinator(jobs, tasks),
                Clock.fixed(now.plusSeconds(1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                new OperationTaskResultFingerprint());
        CompleteOperationTaskRequest request = new CompleteOperationTaskRequest("token", 1,
                "8d77288c-cf64-4ae8-a5be-a4010192fc6e", "{}");
        service.completeTask(1L, 10L, request);
        service.completeTask(1L, 10L, request);
        verify(handler, times(1)).handle(task, "{}");
    }
}
