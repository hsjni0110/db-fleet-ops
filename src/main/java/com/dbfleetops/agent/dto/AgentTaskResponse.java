package com.dbfleetops.agent.dto;

import com.dbfleetops.agent.domain.AgentTask;
import com.dbfleetops.agent.domain.AgentTaskStatus;
import com.dbfleetops.agent.domain.AgentTaskType;

import java.time.LocalDateTime;

public record AgentTaskResponse(
        Long taskId,
        Long agentId,
        AgentTaskType taskType,
        AgentTaskStatus status,
        String parametersJson,
        String resultPayloadJson,
        String errorCode,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
    public static AgentTaskResponse from(
            AgentTask task
    ) {
        return new AgentTaskResponse(
                task.getId(),
                task.getAgentId(),
                task.getTaskType(),
                task.getStatus(),
                task.getParametersJson(),
                task.getResultPayloadJson(),
                task.getErrorCode(),
                task.getErrorMessage(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getCreatedAt()
        );
    }
}