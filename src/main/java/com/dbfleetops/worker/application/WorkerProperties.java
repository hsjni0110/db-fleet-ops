package com.dbfleetops.worker.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "db-fleetops.worker")
public class WorkerProperties {

    private boolean enabled = false;

    private String id = "worker-1";

    private long acquisitionIntervalMs = 5000L;

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

    public long getAcquisitionIntervalMs() {
        return acquisitionIntervalMs;
    }

    public void setAcquisitionIntervalMs(long acquisitionIntervalMs) {
        if (acquisitionIntervalMs <= 0) {
            this.acquisitionIntervalMs = 5000L;
            return;
        }

        this.acquisitionIntervalMs = acquisitionIntervalMs;
    }
}
