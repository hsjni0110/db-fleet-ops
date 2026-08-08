package com.dbfleetops.operation.application;

import com.dbfleetops.operation.application.required.HostStatusWriter;
import com.dbfleetops.operation.domain.*;
import org.springframework.stereotype.Component;

@Component
public class LinuxStatusResultHandler implements TaskResultHandler {
    private final HostStatusWriter writer;
    public LinuxStatusResultHandler(HostStatusWriter writer) { this.writer = writer; }
    public boolean supports(OperationTaskType type) { return type == OperationTaskType.COLLECT_LINUX_STATUS; }
    public void handle(OperationTask task, String payload) { writer.record(task.getAgentId(), payload); }
}
