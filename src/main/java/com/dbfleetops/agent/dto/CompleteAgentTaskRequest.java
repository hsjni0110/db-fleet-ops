package com.dbfleetops.agent.dto;

public record CompleteAgentTaskRequest(
        String agentToken,
        String resultPayloadJson
) {
}