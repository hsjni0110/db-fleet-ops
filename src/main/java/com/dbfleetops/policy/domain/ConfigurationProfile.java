package com.dbfleetops.policy.domain;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration_profile")
public class ConfigurationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String profileName;

    @Enumerated(EnumType.STRING)
    private ConfigurationEngineType engineType;

    private String environment;

    private String versionRange;

    private String description;

    @Enumerated(EnumType.STRING)
    private ConfigurationProfileStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected ConfigurationProfile() {}

    private ConfigurationProfile(String profileName, ConfigurationEngineType engineType,
            String environment, String versionRange, String description) {
        validateRequiredText(profileName, "profileName");
        validateRequiredText(environment, "environment");

        if (engineType == null) {
            throw new IllegalArgumentException("engineType is required.");
        }

        this.profileName = profileName;
        this.engineType = engineType;
        this.environment = environment;
        this.versionRange = versionRange;
        this.description = description;
        this.status = ConfigurationProfileStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static ConfigurationProfile create(String profileName,
            ConfigurationEngineType engineType, String environment, String versionRange,
            String description) {
        return new ConfigurationProfile(profileName, engineType, environment, versionRange,
                description);
    }

    public void activate() {
        this.status = ConfigurationProfileStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.status = ConfigurationProfileStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getProfileName() {
        return profileName;
    }

    public ConfigurationEngineType getEngineType() {
        return engineType;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getVersionRange() {
        return versionRange;
    }

    public String getDescription() {
        return description;
    }

    public ConfigurationProfileStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
