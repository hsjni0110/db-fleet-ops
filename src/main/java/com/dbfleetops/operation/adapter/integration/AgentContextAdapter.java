package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.agent.domain.AgentHostMetric;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.infra.AgentHostMetricRepository;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.operation.application.required.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class AgentContextAdapter implements AgentReader, HostStatusWriter {
    private final AgentRepository agents;
    private final AgentHostMetricRepository metrics;
    private final ObjectMapper objectMapper;
    public AgentContextAdapter(AgentRepository agents, AgentHostMetricRepository metrics,
            ObjectMapper objectMapper) {
        this.agents = agents; this.metrics = metrics; this.objectMapper = objectMapper;
    }
    public Optional<AgentExecutionTarget> findAgent(Long id) {
        return agents.findById(id).map(agent -> new AgentExecutionTarget(agent.getId(),
                agent.getStatus() == AgentStatus.ONLINE));
    }
    public boolean matchesToken(Long id, String token) {
        return agents.findById(id).map(agent -> agent.matchesToken(token)).orElse(false);
    }
    public void record(Long agentId, String payload) {
        try {
            var root = objectMapper.readTree(payload);
            metrics.save(AgentHostMetric.create(agentId, root.path("cpuUsagePercent").asDouble(),
                    root.path("memoryUsagePercent").asDouble(),
                    root.path("diskUsagePercent").asDouble()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid linux status metric payload.", exception);
        }
    }
}
