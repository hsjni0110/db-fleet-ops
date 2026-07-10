package com.dbfleetops.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration_profile_parameter")
public class ConfigurationProfileParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long profileId;

    private String parameterName;

    @Column(columnDefinition = "TEXT")
    private String expectedValue;

    @Enumerated(EnumType.STRING)
    private ParameterValueType valueType;

    private Boolean required;

    private Boolean dynamic;

    private Boolean applyAllowed;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected ConfigurationProfileParameter() {}

    private ConfigurationProfileParameter(Long profileId, String parameterName,
            String expectedValue, ParameterValueType valueType, Boolean required, Boolean dynamic,
            Boolean applyAllowed, String description) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId is required.");
        }

        validateRequiredText(parameterName, "parameterName");
        validateRequiredText(expectedValue, "expectedValue");

        if (valueType == null) {
            throw new IllegalArgumentException("valueType is required.");
        }

        this.profileId = profileId;
        this.parameterName = parameterName;
        this.expectedValue = expectedValue;
        this.valueType = valueType;
        this.required = required != null ? required : true;
        this.dynamic = dynamic != null ? dynamic : false;
        this.applyAllowed = applyAllowed != null ? applyAllowed : false;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static ConfigurationProfileParameter create(Long profileId, String parameterName,
            String expectedValue, ParameterValueType valueType, Boolean required, Boolean dynamic,
            Boolean applyAllowed, String description) {
        return new ConfigurationProfileParameter(profileId, parameterName, expectedValue, valueType,
                required, dynamic, applyAllowed, description);
    }

    public void updateExpectedValue(String expectedValue, ParameterValueType valueType) {
        validateRequiredText(expectedValue, "expectedValue");

        if (valueType == null) {
            throw new IllegalArgumentException("valueType is required.");
        }

        this.expectedValue = expectedValue;
        this.valueType = valueType;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateApplyPolicy(Boolean dynamic, Boolean applyAllowed) {
        this.dynamic = dynamic != null ? dynamic : false;
        this.applyAllowed = applyAllowed != null ? applyAllowed : false;
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

    public Long getProfileId() {
        return profileId;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public ParameterValueType getValueType() {
        return valueType;
    }

    public Boolean getRequired() {
        return required;
    }

    public Boolean getDynamic() {
        return dynamic;
    }

    public Boolean getApplyAllowed() {
        return applyAllowed;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
