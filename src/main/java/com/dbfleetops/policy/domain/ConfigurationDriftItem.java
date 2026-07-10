package com.dbfleetops.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration_drift_item")
public class ConfigurationDriftItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long driftId;

    private String parameterName;

    @Column(columnDefinition = "TEXT")
    private String expectedValue;

    @Column(columnDefinition = "TEXT")
    private String actualValue;

    @Enumerated(EnumType.STRING)
    private ParameterValueType valueType;

    private Boolean required;

    private Boolean dynamic;

    private Boolean applyAllowed;

    @Enumerated(EnumType.STRING)
    private ComplianceStatus complianceStatus;

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDateTime createdAt;

    protected ConfigurationDriftItem() {}

    private ConfigurationDriftItem(Long driftId, String parameterName, String expectedValue,
            String actualValue, ParameterValueType valueType, Boolean required, Boolean dynamic,
            Boolean applyAllowed, ComplianceStatus complianceStatus, String message) {
        if (driftId == null) {
            throw new IllegalArgumentException("driftId is required.");
        }

        validateRequiredText(parameterName, "parameterName");

        if (complianceStatus == null) {
            throw new IllegalArgumentException("complianceStatus is required.");
        }

        this.driftId = driftId;
        this.parameterName = parameterName;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.valueType = valueType;
        this.required = required;
        this.dynamic = dynamic;
        this.applyAllowed = applyAllowed;
        this.complianceStatus = complianceStatus;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public static ConfigurationDriftItem create(Long driftId, String parameterName,
            String expectedValue, String actualValue, ParameterValueType valueType,
            Boolean required, Boolean dynamic, Boolean applyAllowed,
            ComplianceStatus complianceStatus, String message) {
        return new ConfigurationDriftItem(driftId, parameterName, expectedValue, actualValue,
                valueType, required, dynamic, applyAllowed, complianceStatus, message);
    }

    private static void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getDriftId() {
        return driftId;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public String getActualValue() {
        return actualValue;
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

    public ComplianceStatus getComplianceStatus() {
        return complianceStatus;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
