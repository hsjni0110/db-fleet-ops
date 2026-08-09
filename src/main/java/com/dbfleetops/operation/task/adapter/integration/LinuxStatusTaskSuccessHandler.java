package com.dbfleetops.operation.task.adapter.integration;

import com.dbfleetops.operation.task.application.required.HostStatusWriter;
import com.dbfleetops.operation.task.application.required.TaskSuccessHandler;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import org.springframework.stereotype.Component;

/** Linux 상태 수집 Task의 성공 결과를 Agent Host 상태 기록으로 전달합니다. */
@Component
public class LinuxStatusTaskSuccessHandler implements TaskSuccessHandler {

    private final HostStatusWriter writer;

    public LinuxStatusTaskSuccessHandler(HostStatusWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean supports(OperationTaskType taskType) {
        return taskType == OperationTaskType.COLLECT_LINUX_STATUS;
    }

    @Override
    public void handleSuccess(OperationTask task, String resultPayloadJson) {
        writer.record(task.getAgentId(), resultPayloadJson);
    }
}
