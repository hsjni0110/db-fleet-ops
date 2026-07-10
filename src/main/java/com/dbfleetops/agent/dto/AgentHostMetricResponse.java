package com.dbfleetops.agent.dto;

import com.dbfleetops.agent.domain.AgentHostMetric;

import java.time.LocalDateTime;

public record AgentHostMetricResponse(
        Long metricId,
        Long agentId,
        Double cpuUsagePercent,
        Double memoryUsagePercent,
        Double diskUsagePercent,
        LocalDateTime collectedAt
) {
    public static AgentHostMetricResponse from(AgentHostMetric metric) {
        return new AgentHostMetricResponse(
                metric.getId(),
                metric.getAgentId(),
                metric.getCpuUsagePercent(),
                metric.getMemoryUsagePercent(),
                metric.getDiskUsagePercent(),
                metric.getCollectedAt()
        );
    }
}
