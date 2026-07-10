package com.dbfleetops.worker.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "db-fleetops.worker")
public class WorkerProperties {

    private boolean enabled = false;

    private String id = "worker-1";

    private long claimIntervalMs = 5000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            this.id = "worker-1";
            return;
        }

        this.id = id;
    }

    public long getClaimIntervalMs() {
        return claimIntervalMs;
    }

    public void setClaimIntervalMs(long claimIntervalMs) {
        if (claimIntervalMs <= 0) {
            this.claimIntervalMs = 5000L;
            return;
        }

        this.claimIntervalMs = claimIntervalMs;
    }
}
