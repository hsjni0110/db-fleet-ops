package com.dbfleetops.operation.workflow.application.provided;

/** 백업 Task의 성공 결과를 받아 백업 실행 순서를 계속 진행하는 입구입니다. */
public interface BackupTaskResults {

    /** 성공한 백업 또는 복원 검증 Task를 확인하고 다음 단계나 Job 종료를 결정합니다. */
    void continueAfterSuccess(Long taskId, String resultPayloadJson);
}
