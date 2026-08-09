package com.dbfleetops.operation.workflow.application.provided;

import com.dbfleetops.operation.task.dto.OperationTaskResponse;

/** Backup Job을 Agent가 실행할 첫 번째 Task로 시작하는 입구입니다. */
public interface BackupStarter {

    /** 지정한 Backup Job의 논리 백업 Task를 하나만 만듭니다. */
    OperationTaskResponse startBackup(Long jobId, Long databaseId);
}
