package com.dbfleetops.agent.dto;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;

import java.time.LocalDateTime;

public record AgentHeartbeatResponse(
        Long agentId,
        AgentStatus status,
        LocalDateTime lastHeartbeatAt
) {
    public static AgentHeartbeatResponse from(
            Agent agent
    ) {
        return new AgentHeartbeatResponse(
                agent.getId(),
                agent.getStatus(),
                agent.getLastHeartbeatAt()
        );
    }
}