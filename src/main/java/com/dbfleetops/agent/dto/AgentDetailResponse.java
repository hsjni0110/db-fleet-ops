package com.dbfleetops.agent.dto;

import com.dbfleetops.operation.dto.OperationTaskResponse;

import java.util.List;

public record AgentDetailResponse(
        AgentConsoleResponse agent,
        List<AgentHostMetricResponse> recentHostMetrics,
        List<OperationTaskResponse> recentOperationTasks
) {
}
