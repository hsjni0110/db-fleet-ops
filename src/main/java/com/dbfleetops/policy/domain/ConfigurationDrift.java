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
@Table(name = "configuration_drift")
public class ConfigurationDrift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long databaseId;

    private Long profileId;

    private Long snapshotId;

    @Enumerated(EnumType.STRING)
    private ConfigurationEngineType engineType;

    @Enumerated(EnumType.STRING)
    private ConfigurationDriftStatus status;

    private Integer totalCount;

    private Integer compliantCount;

    private Integer nonCompliantCount;

    private Integer missingCount;

    private LocalDateTime checkedAt;

    protected ConfigurationDrift() {}

    private ConfigurationDrift(Long databaseId, Long profileId, Long snapshotId,
            ConfigurationEngineType engineType, ConfigurationDriftStatus status, Integer totalCount,
            Integer compliantCount, Integer nonCompliantCount, Integer missingCount) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        if (profileId == null) {
            throw new IllegalArgumentException("profileId is required.");
        }

        if (snapshotId == null) {
            throw new IllegalArgumentException("snapshotId is required.");
        }

        if (engineType == null) {
            throw new IllegalArgumentException("engineType is required.");
        }

        if (status == null) {
            throw new IllegalArgumentException("status is required.");
        }

        this.databaseId = databaseId;
        this.profileId = profileId;
        this.snapshotId = snapshotId;
        this.engineType = engineType;
        this.status = status;
        this.totalCount = totalCount;
        this.compliantCount = compliantCount;
        this.nonCompliantCount = nonCompliantCount;
        this.missingCount = missingCount;
        this.checkedAt = LocalDateTime.now();
    }

    public static ConfigurationDrift create(Long databaseId, Long profileId, Long snapshotId,
            ConfigurationEngineType engineType, ConfigurationDriftStatus status, Integer totalCount,
            Integer compliantCount, Integer nonCompliantCount, Integer missingCount) {
        return new ConfigurationDrift(databaseId, profileId, snapshotId, engineType, status,
                totalCount, compliantCount, nonCompliantCount, missingCount);
    }

    public Long getId() {
        return id;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public Long getProfileId() {
        return profileId;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public ConfigurationEngineType getEngineType() {
        return engineType;
    }

    public ConfigurationDriftStatus getStatus() {
        return status;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public Integer getCompliantCount() {
        return compliantCount;
    }

    public Integer getNonCompliantCount() {
        return nonCompliantCount;
    }

    public Integer getMissingCount() {
        return missingCount;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }
}
