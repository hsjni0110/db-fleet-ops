package com.dbfleetops.operation.application.required;


/** Operation이 백업 복원 검증 결과를 Backup 영역에 저장할 때 사용하는 출구입니다. */
public interface BackupVerificationWriter {
    /** Agent가 보낸 복원 검증 결과를 검사하고 저장한 뒤 읽기 쉬운 결과로 반환합니다. */
    RestoreVerificationOutcome record(Long jobId, Long taskId, String resultPayloadJson);
}
