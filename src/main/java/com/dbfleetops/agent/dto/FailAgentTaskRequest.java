package com.dbfleetops.agent.dto;

public record FailAgentTaskRequest(
        String agentToken,
        String errorCode,
        String errorMessage
) {
}