package com.dbfleetops.agent.domain;

import lombok.Getter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.state;

@Getter
@Entity
@Table(name = "agent")
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String agentName;

    private String hostname;

    private String ipAddress;

    private String osName;

    private String architecture;

    private String agentVersion;

    private String agentToken;

    @Enumerated(EnumType.STRING)
    private AgentStatus status;

    private LocalDateTime lastHeartbeatAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected Agent() {}

    private Agent(String agentName, String hostname, String ipAddress, String osName,
            String architecture, String agentVersion, String agentToken) {
        hasText(agentName, "Agent 이름은 필수입니다.");
        hasText(hostname, "호스트 이름은 필수입니다.");
        hasText(ipAddress, "IP 주소는 필수입니다.");
        hasText(osName, "운영체제 이름은 필수입니다.");
        hasText(agentVersion, "Agent 버전은 필수입니다.");
        hasText(agentToken, "Agent 토큰은 필수입니다.");

        this.agentName = agentName;
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.osName = osName;
        this.architecture = normalizeArchitecture(architecture);
        this.agentVersion = agentVersion;
        this.agentToken = agentToken;
        this.status = AgentStatus.ONLINE;
        this.lastHeartbeatAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static Agent register(String agentName, String hostname, String ipAddress, String osName,
            String architecture, String agentVersion, String agentToken) {
        return new Agent(agentName, hostname, ipAddress, osName, architecture, agentVersion,
                agentToken);
    }

    public static Agent register(String agentName, String hostname, String ipAddress, String osName,
            String agentVersion, String agentToken) {
        return register(agentName, hostname, ipAddress, osName, "unknown", agentVersion,
                agentToken);
    }

    private static String normalizeArchitecture(String architecture) {
        if (architecture == null || architecture.isBlank()) {
            return "unknown";
        }

        return architecture;
    }

    public void recordHeartbeat() {
        state(status != null, "Agent 상태가 존재해야 합니다.");
        state(status != AgentStatus.DISABLED, "비활성화된 Agent는 heartbeat를 기록할 수 없습니다.");

        this.status = AgentStatus.ONLINE;
        this.lastHeartbeatAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markOffline() {
        state(status != null, "Agent 상태가 존재해야 합니다.");
        state(status != AgentStatus.DISABLED, "비활성화된 Agent는 offline 상태로 변경할 수 없습니다.");
        state(status != AgentStatus.OFFLINE, "이미 offline 상태인 Agent입니다.");

        this.status = AgentStatus.OFFLINE;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        state(status != null, "Agent 상태가 존재해야 합니다.");
        state(status != AgentStatus.DISABLED, "이미 비활성화된 Agent입니다.");

        this.status = AgentStatus.DISABLED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean matchesToken(String token) {
        return agentToken != null && agentToken.equals(token);
    }

}
