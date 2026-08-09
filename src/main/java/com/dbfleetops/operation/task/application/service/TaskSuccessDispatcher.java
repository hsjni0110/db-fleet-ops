package com.dbfleetops.operation.task.application.service;

import com.dbfleetops.operation.task.application.required.TaskSuccessHandler;
import com.dbfleetops.operation.task.domain.OperationTask;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

/** 최초로 성공한 Task를 그 결과를 이해하는 처리기 하나에 전달합니다. */
@Component
public class TaskSuccessDispatcher {

    private final List<TaskSuccessHandler> handlers;

    public TaskSuccessDispatcher(List<TaskSuccessHandler> handlers) {
        this.handlers = handlers;
    }

    public void dispatch(OperationTask task, String resultPayloadJson) {
        notNull(task, "Task는 필수입니다.");

        List<TaskSuccessHandler> supportedHandlers = handlers.stream()
                .filter(handler -> handler.supports(task.getTaskType()))
                .toList();

        state(supportedHandlers.size() == 1,
                "Task 성공 처리기는 정확히 하나여야 합니다. taskType=" + task.getTaskType()
                        + ", handlerCount=" + supportedHandlers.size());

        supportedHandlers.getFirst().handleSuccess(task, resultPayloadJson);
    }
}
