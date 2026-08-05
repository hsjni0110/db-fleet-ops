package com.dbfleetops.agent.domain;

import lombok.Getter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

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

    protected Agent() {
    }

    private Agent(
            String agentName,
            String hostname,
            String ipAddress,
            String osName,
            String architecture,
            String agentVersion,
            String agentToken
    ) {
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

    public static Agent register(
            String agentName,
            String hostname,
            String ipAddress,
            String osName,
            String architecture,
            String agentVersion,
            String agentToken
    ) {
        return new Agent(
                agentName,
                hostname,
                ipAddress,
                osName,
                architecture,
                agentVersion,
                agentToken
        );
    }

    public static Agent register(
            String agentName,
            String hostname,
            String ipAddress,
            String osName,
            String agentVersion,
            String agentToken
    ) {
        return register(
                agentName,
                hostname,
                ipAddress,
                osName,
                "unknown",
                agentVersion,
                agentToken
        );
    }

    private static String normalizeArchitecture(String architecture) {
        if (architecture == null || architecture.isBlank()) {
            return "unknown";
        }

        return architecture;
    }

    public void heartbeat() {
        if (status == AgentStatus.DISABLED) {
            throw new IllegalStateException(
                    "Disabled agent cannot update heartbeat."
            );
        }

        this.status = AgentStatus.ONLINE;
        this.lastHeartbeatAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markOffline() {
        if (status == AgentStatus.DISABLED) {
            return;
        }

        this.status = AgentStatus.OFFLINE;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        this.status = AgentStatus.DISABLED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean tokenMatches(
            String token
    ) {
        return agentToken != null && agentToken.equals(token);
    }












}
