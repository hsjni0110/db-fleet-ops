package com.dbfleetops.agent.dto;

import com.dbfleetops.agent.domain.AgentTaskType;

public record CreateAgentTaskRequest(
        Long agentId,
        AgentTaskType taskType,
        String parametersJson
) {
}