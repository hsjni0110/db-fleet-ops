package com.dbfleetops.backup.dto;

/** Agent가 보고한 Table 단위 복원 검증 결과입니다. */
public record RestoreVerificationItemResult(String tableName, Boolean existsInRestoredDb,
        Long rowCount, String status, String message) {}
