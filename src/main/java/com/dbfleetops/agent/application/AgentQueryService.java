package com.dbfleetops.agent.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.dto.AgentConsoleResponse;
import com.dbfleetops.agent.dto.AgentDetailResponse;
import com.dbfleetops.agent.dto.AgentHostMetricResponse;
import com.dbfleetops.agent.infra.AgentHostMetricRepository;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.operation.dto.OperationTaskResponse;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentQueryService {

    private final AgentRepository agentRepository;
    private final AgentHostMetricRepository hostMetricRepository;
    private final OperationTaskRepository operationTaskRepository;

    public AgentQueryService(
            AgentRepository agentRepository,
            AgentHostMetricRepository hostMetricRepository,
            OperationTaskRepository operationTaskRepository
    ) {
        this.agentRepository = agentRepository;
        this.hostMetricRepository = hostMetricRepository;
        this.operationTaskRepository = operationTaskRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentConsoleResponse> findAll() {
        LocalDateTime now = LocalDateTime.now();

        return agentRepository.findAll()
                .stream()
                .map(agent -> AgentConsoleResponse.from(agent, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentDetailResponse findById(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent not found. agentId=" + agentId
                ));

        List<AgentHostMetricResponse> metrics =
                hostMetricRepository.findTop10ByAgentIdOrderByCollectedAtDesc(agentId)
                        .stream()
                        .map(AgentHostMetricResponse::from)
                        .toList();

        List<OperationTaskResponse> tasks =
                operationTaskRepository.findTop10ByAgentIdOrderByCreatedAtDesc(agentId)
                        .stream()
                        .map(OperationTaskResponse::from)
                        .toList();

        return new AgentDetailResponse(
                AgentConsoleResponse.from(agent),
                metrics,
                tasks
        );
    }
}
