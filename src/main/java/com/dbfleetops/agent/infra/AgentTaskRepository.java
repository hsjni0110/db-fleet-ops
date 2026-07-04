package com.dbfleetops.agent.infra;

import com.dbfleetops.agent.domain.AgentTask;
import com.dbfleetops.agent.domain.AgentTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {

    List<AgentTask> findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(
            Long agentId,
            AgentTaskStatus status
    );
}