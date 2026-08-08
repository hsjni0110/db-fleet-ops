package com.dbfleetops.operation.application;

import com.dbfleetops.operation.domain.OperationTask;
import org.springframework.stereotype.Component;
import java.util.List;

/** 성공한 Task를 그 결과를 이해하는 Handler에 전달합니다. */
@Component
public class TaskResultDispatcher {
    private final List<TaskResultHandler> handlers;
    public TaskResultDispatcher(List<TaskResultHandler> handlers) { this.handlers = handlers; }
    public void dispatch(OperationTask task, String payload) {
        handlers.stream().filter(handler -> handler.supports(task.getTaskType())).findFirst()
                .ifPresent(handler -> handler.handle(task, payload));
    }
}
