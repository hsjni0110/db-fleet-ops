package com.dbfleetops.agent.domain;

import java.time.LocalDateTime;

public class Agent {

    private final Long id;
    private final String agentName;
    private final String hostname;
    private final String ipAddress;
    private final String osName;
    private final String agentVersion;
    private final String agentToken;

    private AgentStatus status;
    private LocalDateTime lastHeartbeatAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Agent(
            Long id,
            String agentName,
            String hostname,
            String ipAddress,
            String osName,
            String agentVersion,
            String agentToken
    ) {
        this.id = id;
        this.agentName = agentName;
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.osName = osName;
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
            String agentVersion,
            String agentToken
    ) {
        return new Agent(
                null,
                agentName,
                hostname,
                ipAddress,
                osName,
                agentVersion,
                agentToken
        );
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

    public Long getId() {
        return id;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getHostname() {
        return hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getOsName() {
        return osName;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public String getAgentToken() {
        return agentToken;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}