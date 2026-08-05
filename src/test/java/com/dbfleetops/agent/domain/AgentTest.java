package com.dbfleetops.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AgentTest {

    @Test
    void registerCreatesOnlineAgent() {
        Agent agent =
                Agent.register(
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "amd64",
                        "0.1.0",
                        "token-001"
                );

        assertThat(agent.getAgentName())
                .isEqualTo("local-agent");

        assertThat(agent.getHostname())
                .isEqualTo("localhost");

        assertThat(agent.getArchitecture())
                .isEqualTo("amd64");

        assertThat(agent.getStatus())
                .isEqualTo(AgentStatus.ONLINE);

        assertThat(agent.getLastHeartbeatAt())
                .isNotNull();

        assertThat(agent.matchesToken("token-001"))
                .isTrue();

        assertThat(agent.matchesToken("wrong-token"))
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

        agent.recordHeartbeat();

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

        assertThatIllegalStateException()
                .isThrownBy(agent::recordHeartbeat)
                .withMessage("비활성화된 Agent는 heartbeat를 기록할 수 없습니다.");
    }

    @Test
    void registerRejectsMissingRequiredValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Agent.register(null, "localhost", "127.0.0.1", "Linux",
                        "amd64", "0.1.0", "token-001"))
                .withMessage("Agent 이름은 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Agent.register("local-agent", " ", "127.0.0.1", "Linux",
                        "amd64", "0.1.0", "token-001"))
                .withMessage("호스트 이름은 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Agent.register("local-agent", "localhost", null, "Linux",
                        "amd64", "0.1.0", "token-001"))
                .withMessage("IP 주소는 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Agent.register("local-agent", "localhost", "127.0.0.1", " ",
                        "amd64", "0.1.0", "token-001"))
                .withMessage("운영체제 이름은 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Agent.register("local-agent", "localhost", "127.0.0.1",
                        "Linux", "amd64", null, "token-001"))
                .withMessage("Agent 버전은 필수입니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Agent.register("local-agent", "localhost", "127.0.0.1",
                        "Linux", "amd64", "0.1.0", " "))
                .withMessage("Agent 토큰은 필수입니다.");
    }

    @Test
    void registerNormalizesMissingArchitecture() {
        Agent agent = Agent.register("local-agent", "localhost", "127.0.0.1", "Linux", " ",
                "0.1.0", "token-001");

        assertThat(agent.getArchitecture()).isEqualTo("unknown");
    }

    @Test
    void disabledAgentCannotBeMarkedOffline() {
        Agent agent = registeredAgent();
        agent.disable();

        assertThatIllegalStateException()
                .isThrownBy(agent::markOffline)
                .withMessage("비활성화된 Agent는 offline 상태로 변경할 수 없습니다.");
    }

    @Test
    void offlineAgentCannotBeMarkedOfflineAgain() {
        Agent agent = registeredAgent();
        agent.markOffline();

        assertThatIllegalStateException()
                .isThrownBy(agent::markOffline)
                .withMessage("이미 offline 상태인 Agent입니다.");
    }

    @Test
    void disabledAgentCannotBeDisabledAgain() {
        Agent agent = registeredAgent();
        agent.disable();

        assertThatIllegalStateException()
                .isThrownBy(agent::disable)
                .withMessage("이미 비활성화된 Agent입니다.");
    }

    @Test
    void nullTokenDoesNotMatch() {
        Agent agent = registeredAgent();

        assertThat(agent.matchesToken(null)).isFalse();
    }

    private Agent registeredAgent() {
        return Agent.register("local-agent", "localhost", "127.0.0.1", "Linux", "amd64",
                "0.1.0", "token-001");
    }
}
