package com.dbfleetops.operation.application.required;

/** Operation이 백업 Payload의 세부 형식을 모르고 다음 단계를 준비하도록 돕는 출구입니다. */
public interface BackupPayloadBuilder {
    boolean shouldVerifyAfterBackup(String backupParametersJson);
    String createRestorePayload(Long jobId, Long backupTaskId, String parametersJson,
            String resultPayloadJson);
}
