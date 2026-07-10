package com.dbfleetops.agent.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.dto.AgentHeartbeatRequest;
import com.dbfleetops.agent.dto.AgentHeartbeatResponse;
import com.dbfleetops.agent.dto.RegisterAgentRequest;
import com.dbfleetops.agent.dto.RegisterAgentResponse;
import com.dbfleetops.agent.infra.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Test
    void registerCreatesOnlineAgentAndReturnsToken() {
        RegisterAgentRequest request =
                new RegisterAgentRequest(
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "amd64",
                        "0.1.0"
                );

        when(agentRepository.save(any(Agent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AgentService service =
                new AgentService(agentRepository);

        RegisterAgentResponse response =
                service.register(request);

        assertThat(response.status())
                .isEqualTo(AgentStatus.ONLINE);

        assertThat(response.agentToken())
                .startsWith("agent-token-");

        verify(agentRepository)
                .save(any(Agent.class));
    }

    @Test
    void registerStoresUnknownArchitectureWhenBlank() {
        RegisterAgentRequest request =
                new RegisterAgentRequest(
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        " ",
                        "0.1.0"
                );

        when(agentRepository.save(any(Agent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AgentService service =
                new AgentService(agentRepository);

        service.register(request);

        verify(agentRepository)
                .save(argThat(agent -> "unknown".equals(agent.getArchitecture())));
    }

    @Test
    void heartbeatUpdatesAgentStatus() {
        Agent agent =
                Agent.register(
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "0.1.0",
                        "agent-token-001"
                );

        agent.markOffline();

        when(agentRepository.findById(1L))
                .thenReturn(Optional.of(agent));

        AgentService service =
                new AgentService(agentRepository);

        AgentHeartbeatResponse response =
                service.heartbeat(
                        1L,
                        new AgentHeartbeatRequest(
                                "agent-token-001",
                                12.5,
                                61.2,
                                73.8
                        )
                );

        assertThat(response.status())
                .isEqualTo(AgentStatus.ONLINE);

        assertThat(response.lastHeartbeatAt())
                .isNotNull();
    }

    @Test
    void heartbeatThrowsExceptionWhenTokenIsInvalid() {
        Agent agent =
                Agent.register(
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "0.1.0",
                        "agent-token-001"
                );

        when(agentRepository.findById(1L))
                .thenReturn(Optional.of(agent));

        AgentService service =
                new AgentService(agentRepository);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.heartbeat(
                        1L,
                        new AgentHeartbeatRequest(
                                "wrong-token",
                                12.5,
                                61.2,
                                73.8
                        )
                )
        );
    }
}
