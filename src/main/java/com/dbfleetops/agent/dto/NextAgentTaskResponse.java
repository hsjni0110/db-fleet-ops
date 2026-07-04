package com.dbfleetops.agent.dto;

import com.dbfleetops.agent.domain.AgentTask;
import com.dbfleetops.agent.domain.AgentTaskType;

public record NextAgentTaskResponse(
        boolean hasTask,
        Long taskId,
        AgentTaskType taskType,
        String parametersJson
) {
    public static NextAgentTaskResponse from(
            AgentTask task
    ) {
        return new NextAgentTaskResponse(
                true,
                task.getId(),
                task.getTaskType(),
                task.getParametersJson()
        );
    }

    public static NextAgentTaskResponse empty() {
        return new NextAgentTaskResponse(
                false,
                null,
                null,
                null
        );
    }
}