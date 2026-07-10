package com.dbfleetops.agent.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.dto.AgentHeartbeatRequest;
import com.dbfleetops.agent.dto.AgentHeartbeatResponse;
import com.dbfleetops.agent.dto.RegisterAgentRequest;
import com.dbfleetops.agent.dto.RegisterAgentResponse;
import com.dbfleetops.agent.infra.AgentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AgentService {

    private final AgentRepository agentRepository;

    public AgentService(
            AgentRepository agentRepository
    ) {
        this.agentRepository = agentRepository;
    }

    @Transactional
    public RegisterAgentResponse register(
            RegisterAgentRequest request
    ) {
        Agent agent =
                Agent.register(
                        request.agentName(),
                        request.hostname(),
                        request.ipAddress(),
                        request.osName(),
                        request.architecture(),
                        request.agentVersion(),
                        generateAgentToken()
                );

        Agent savedAgent =
                agentRepository.save(agent);

        return RegisterAgentResponse.from(savedAgent);
    }

    @Transactional
    public AgentHeartbeatResponse heartbeat(
            Long agentId,
            AgentHeartbeatRequest request
    ) {
        Agent agent =
                agentRepository.findById(agentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Agent not found. agentId=" + agentId
                        ));

        if (!agent.tokenMatches(request.agentToken())) {
            throw new IllegalArgumentException(
                    "Invalid agent token. agentId=" + agentId
            );
        }

        agent.heartbeat();

        return AgentHeartbeatResponse.from(agent);
    }

    private String generateAgentToken() {
        return "agent-token-" + UUID.randomUUID();
    }
}
