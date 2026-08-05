package com.dbfleetops.agent.domain;

import lombok.Getter;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "agent_host_metric")
public class AgentHostMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;

    private Double cpuUsagePercent;

    private Double memoryUsagePercent;

    private Double diskUsagePercent;

    private LocalDateTime collectedAt;

    protected AgentHostMetric() {}

    private AgentHostMetric(Long agentId, Double cpuUsagePercent, Double memoryUsagePercent,
            Double diskUsagePercent) {
        this.agentId = agentId;
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsagePercent = memoryUsagePercent;
        this.diskUsagePercent = diskUsagePercent;
        this.collectedAt = LocalDateTime.now();
    }

    public static AgentHostMetric create(Long agentId, Double cpuUsagePercent,
            Double memoryUsagePercent, Double diskUsagePercent) {
        return new AgentHostMetric(agentId, cpuUsagePercent, memoryUsagePercent, diskUsagePercent);
    }

}
