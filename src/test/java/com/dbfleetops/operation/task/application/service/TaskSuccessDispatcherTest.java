package com.dbfleetops.operation.task.application.service;

import com.dbfleetops.operation.task.application.required.TaskSuccessHandler;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskSuccessDispatcherTest {

    private final OperationTask task = OperationTask.create(
            1L, OperationTaskType.COLLECT_LINUX_STATUS, "{}");

    @Test
    void matchingHandlerProcessesSuccess() {
        TaskSuccessHandler handler = supportedHandler();
        TaskSuccessDispatcher dispatcher = new TaskSuccessDispatcher(List.of(handler));

        dispatcher.dispatch(task, "{}");

        verify(handler).handleSuccess(task, "{}");
    }

    @Test
    void missingHandlerIsRejected() {
        TaskSuccessDispatcher dispatcher = new TaskSuccessDispatcher(List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> dispatcher.dispatch(task, "{}"))
                .withMessageContaining("handlerCount=0");
    }

    @Test
    void duplicateHandlersAreRejected() {
        TaskSuccessHandler first = supportedHandler();
        TaskSuccessHandler second = supportedHandler();
        TaskSuccessDispatcher dispatcher = new TaskSuccessDispatcher(List.of(first, second));

        assertThatIllegalStateException()
                .isThrownBy(() -> dispatcher.dispatch(task, "{}"))
                .withMessageContaining("handlerCount=2");

        verify(first, never()).handleSuccess(task, "{}");
        verify(second, never()).handleSuccess(task, "{}");
    }

    private TaskSuccessHandler supportedHandler() {
        TaskSuccessHandler handler = mock(TaskSuccessHandler.class);
        when(handler.supports(OperationTaskType.COLLECT_LINUX_STATUS)).thenReturn(true);
        return handler;
    }
}
