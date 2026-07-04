package com.dbfleetops.agent.dto;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;

public record RegisterAgentResponse(
        Long agentId,
        String agentToken,
        AgentStatus status
) {
    public static RegisterAgentResponse from(
            Agent agent
    ) {
        return new RegisterAgentResponse(
                agent.getId(),
                agent.getAgentToken(),
                agent.getStatus()
        );
    }
}