package com.dbfleetops.agent.dto;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record AgentConsoleResponse(
        Long agentId,
        String agentName,
        String hostname,
        String ipAddress,
        String osName,
        String architecture,
        String agentVersion,
        AgentStatus status,
        LocalDateTime lastHeartbeatAt,
        Long heartbeatDelaySeconds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AgentConsoleResponse from(Agent agent) {
        return from(agent, LocalDateTime.now());
    }

    public static AgentConsoleResponse from(Agent agent, LocalDateTime now) {
        return new AgentConsoleResponse(
                agent.getId(),
                agent.getAgentName(),
                agent.getHostname(),
                agent.getIpAddress(),
                agent.getOsName(),
                agent.getArchitecture(),
                agent.getAgentVersion(),
                agent.getStatus(),
                agent.getLastHeartbeatAt(),
                heartbeatDelaySeconds(agent.getLastHeartbeatAt(), now),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }

    private static Long heartbeatDelaySeconds(LocalDateTime lastHeartbeatAt, LocalDateTime now) {
        if (lastHeartbeatAt == null) {
            return null;
        }

        return Math.max(0L, ChronoUnit.SECONDS.between(lastHeartbeatAt, now));
    }
}
