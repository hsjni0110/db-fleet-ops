package com.dbfleetops.operation.task.application.result;

import com.dbfleetops.operation.task.application.required.HostStatusWriter;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import org.springframework.stereotype.Component;

@Component
public class LinuxStatusResultHandler implements TaskResultHandler {
    private final HostStatusWriter writer;
    public LinuxStatusResultHandler(HostStatusWriter writer) { this.writer = writer; }
    public boolean supports(OperationTaskType type) { return type == OperationTaskType.COLLECT_LINUX_STATUS; }
    public void handle(OperationTask task, String payload) { writer.record(task.getAgentId(), payload); }
}
