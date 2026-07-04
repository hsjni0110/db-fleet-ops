package com.dbfleetops.agent.dto;

public record RegisterAgentRequest(
        String agentName,
        String hostname,
        String ipAddress,
        String osName,
        String agentVersion
) {
}