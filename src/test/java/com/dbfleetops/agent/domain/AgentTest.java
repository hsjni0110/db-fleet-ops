package com.dbfleetops.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTest {

    @Test
    void registerCreatesOnlineAgent() {
        Agent agent =
                Agent.register(
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "0.1.0",
                        "token-001"
                );

        assertThat(agent.getAgentName())
                .isEqualTo("local-agent");

        assertThat(agent.getHostname())
                .isEqualTo("localhost");

        assertThat(agent.getStatus())
                .isEqualTo(AgentStatus.ONLINE);

        assertThat(agent.getLastHeartbeatAt())
                .isNotNull();

        assertThat(agent.tokenMatches("token-001"))
                .isTrue();

        assertThat(agent.tokenMatches("wrong-token"))
                .isFalse();
    }

    @Test
    void heartbeatUpdatesAgentAsOnline() {
        Agent agent =
                Agent.register(
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "0.1.0",
                        "token-001"
                );

        agent.markOffline();

        assertThat(agent.getStatus())
                .isEqualTo(AgentStatus.OFFLINE);

        agent.heartbeat();

        assertThat(agent.getStatus())
                .isEqualTo(AgentStatus.ONLINE);

        assertThat(agent.getLastHeartbeatAt())
                .isNotNull();
    }

    @Test
    void disabledAgentCannotHeartbeat() {
        Agent agent =
                Agent.register(
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "0.1.0",
                        "token-001"
                );

        agent.disable();

        assertThrows(
                IllegalStateException.class,
                agent::heartbeat
        );
    }
}