package com.dbfleetops.agent.infra;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    Optional<Agent> findFirstByStatusOrderByLastHeartbeatAtDesc(AgentStatus status);

    long countByStatus(AgentStatus status);
}
