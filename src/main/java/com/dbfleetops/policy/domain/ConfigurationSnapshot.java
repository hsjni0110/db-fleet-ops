package com.dbfleetops.policy.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration_snapshot")
public class ConfigurationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long databaseId;

    @Enumerated(EnumType.STRING)
    private ConfigurationEngineType engineType;

    private LocalDateTime capturedAt;

    protected ConfigurationSnapshot() {}

    private ConfigurationSnapshot(Long databaseId, ConfigurationEngineType engineType) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        if (engineType == null) {
            throw new IllegalArgumentException("engineType is required.");
        }

        this.databaseId = databaseId;
        this.engineType = engineType;
        this.capturedAt = LocalDateTime.now();
    }

    public static ConfigurationSnapshot create(Long databaseId,
            ConfigurationEngineType engineType) {
        return new ConfigurationSnapshot(databaseId, engineType);
    }

    public Long getId() {
        return id;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public ConfigurationEngineType getEngineType() {
        return engineType;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }
}
