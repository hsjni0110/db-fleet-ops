package com.dbfleetops.operation.application.required;

/** Task에 기록할 Credential 식별 정보입니다. */
public record CredentialReference(Long id, Long databaseId) {}
