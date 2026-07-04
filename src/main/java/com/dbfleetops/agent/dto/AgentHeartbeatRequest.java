package com.dbfleetops.agent.dto;

public record AgentHeartbeatRequest(
        String agentToken,
        double cpuUsagePercent,
        double memoryUsagePercent,
        double diskUsagePercent
) {
}