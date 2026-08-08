package com.dbfleetops.operation.workflow.application.provided;

import com.dbfleetops.operation.task.dto.OperationTaskResponse;

/** Backup Job을 Agent용 Task 흐름으로 바꾸는 입구입니다. */
public interface BackupTasks {
    /** 지정한 Job에 논리 백업 Task를 하나만 만듭니다. */
    OperationTaskResponse createBackupTask(Long jobId, Long databaseId);
}
