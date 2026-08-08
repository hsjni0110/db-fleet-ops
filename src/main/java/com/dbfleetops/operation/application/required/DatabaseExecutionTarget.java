package com.dbfleetops.operation.application.required;

/** Task를 만들 때 필요한 Database의 최소 연결 정보입니다. */
public record DatabaseExecutionTarget(Long id, String databaseName, String host, int port,
        String engine, Long assignedAgentId, boolean active) {}
