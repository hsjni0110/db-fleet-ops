package com.dbfleetops.operation.shared.application.required;

/** 유효한 Task가 실행 직전에 사용할 Database 계정 정보입니다. */
public record ResolvedTaskCredential(Long databaseId, String username, String password) {}
