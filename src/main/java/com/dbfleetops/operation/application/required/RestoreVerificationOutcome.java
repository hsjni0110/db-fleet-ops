package com.dbfleetops.operation.application.required;

/** 복원 검증 결과 중 Job 판정에 필요한 값입니다. */
public record RestoreVerificationOutcome(boolean verified, String errorCode, String errorMessage) {}
