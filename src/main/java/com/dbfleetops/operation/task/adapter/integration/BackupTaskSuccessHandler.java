package com.dbfleetops.operation.task.adapter.integration;

import com.dbfleetops.operation.task.application.required.TaskSuccessHandler;
import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;
import com.dbfleetops.operation.workflow.application.provided.BackupTaskResults;
import org.springframework.stereotype.Component;

/** 백업 Task의 성공 결과를 백업 실행 순서에 전달합니다. */
@Component
public class BackupTaskSuccessHandler implements TaskSuccessHandler {

    private final BackupTaskResults backupTaskResults;

    public BackupTaskSuccessHandler(BackupTaskResults backupTaskResults) {
        this.backupTaskResults = backupTaskResults;
    }

    @Override
    public boolean supports(OperationTaskType taskType) {
        return taskType == OperationTaskType.MYSQL_LOGICAL_BACKUP
                || taskType == OperationTaskType.MYSQL_RESTORE_VERIFY;
    }

    @Override
    public void handleSuccess(OperationTask task, String resultPayloadJson) {
        backupTaskResults.continueAfterSuccess(task.getId(), resultPayloadJson);
    }
}
