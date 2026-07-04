package com.dbfleetops.agent.infra;

import com.dbfleetops.agent.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long> {
}