package com.dbfleetops.operation.application.required;

/** Operation이 Agent에 대해 알아야 하는 최소 실행 정보입니다. */
public record AgentExecutionTarget(Long id, boolean online) {}
