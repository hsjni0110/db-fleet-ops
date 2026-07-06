package com.dbfleetops.agent.integration;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.infra.AgentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AgentPersistenceTest {

        @Autowired
        private AgentRepository agentRepository;


        @Test
        void saveAndFindAgent() {
                Agent agent = Agent.register("local-agent", "localhost", "127.0.0.1", "Linux",
                                "0.1.0", "agent-token-001");

                Agent savedAgent = agentRepository.save(agent);

                Agent foundAgent = agentRepository.findById(savedAgent.getId()).orElseThrow();

                assertThat(foundAgent.getAgentName()).isEqualTo("local-agent");

                assertThat(foundAgent.getHostname()).isEqualTo("localhost");

                assertThat(foundAgent.getStatus()).isEqualTo(AgentStatus.ONLINE);

                assertThat(foundAgent.getAgentToken()).isEqualTo("agent-token-001");

                assertThat(foundAgent.getLastHeartbeatAt()).isNotNull();
        }

        @Test
        void heartbeatUpdateIsPersisted() {
                Agent agent = Agent.register("local-agent", "localhost", "127.0.0.1", "Linux",
                                "0.1.0", "agent-token-001");

                Agent savedAgent = agentRepository.save(agent);

                savedAgent.markOffline();

                agentRepository.flush();

                Agent offlineAgent = agentRepository.findById(savedAgent.getId()).orElseThrow();

                assertThat(offlineAgent.getStatus()).isEqualTo(AgentStatus.OFFLINE);

                offlineAgent.heartbeat();

                agentRepository.flush();

                Agent onlineAgent = agentRepository.findById(savedAgent.getId()).orElseThrow();

                assertThat(onlineAgent.getStatus()).isEqualTo(AgentStatus.ONLINE);

                assertThat(onlineAgent.getLastHeartbeatAt()).isNotNull();
        }

        @Test
        void disabledStatusIsPersisted() {
                Agent agent = Agent.register("local-agent", "localhost", "127.0.0.1", "Linux",
                                "0.1.0", "agent-token-001");

                Agent savedAgent = agentRepository.save(agent);

                savedAgent.disable();

                agentRepository.flush();

                Agent foundAgent = agentRepository.findById(savedAgent.getId()).orElseThrow();

                assertThat(foundAgent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        }
}
