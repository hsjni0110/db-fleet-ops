package com.dbfleetops.agent.infra;

import com.dbfleetops.agent.domain.AgentHostMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentHostMetricRepository extends JpaRepository<AgentHostMetric, Long> {

    List<AgentHostMetric> findTop10ByAgentIdOrderByCollectedAtDesc(Long agentId);
}
