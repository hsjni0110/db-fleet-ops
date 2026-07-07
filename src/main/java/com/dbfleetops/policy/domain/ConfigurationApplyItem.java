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
@Table(name = "configuration_apply_item")
public class ConfigurationApplyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long applyId;

    private String parameterName;

    private String requestedValue;

    private String beforeValue;

    private String afterValue;

    @Enumerated(EnumType.STRING)
    private ParameterValueType valueType;

    private Boolean dynamic;

    private Boolean applyAllowed;

    @Enumerated(EnumType.STRING)
    private ConfigurationApplyItemStatus applyStatus;

    private String failureCode;

    private String failureMessage;

    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;

    private LocalDateTime verifiedAt;

    protected ConfigurationApplyItem() {}

    private ConfigurationApplyItem(Long applyId, String parameterName, String requestedValue,
            ParameterValueType valueType, Boolean dynamic, Boolean applyAllowed) {
        if (applyId == null) {
            throw new IllegalArgumentException("applyId is required.");
        }

        validateRequiredText(parameterName, "parameterName");

        validateRequiredText(requestedValue, "requestedValue");

        if (valueType == null) {
            throw new IllegalArgumentException("valueType is required.");
        }

        this.applyId = applyId;
        this.parameterName = parameterName;
        this.requestedValue = requestedValue;
        this.valueType = valueType;
        this.dynamic = dynamic;
        this.applyAllowed = applyAllowed;
        this.applyStatus = ConfigurationApplyItemStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static ConfigurationApplyItem create(Long applyId, String parameterName,
            String requestedValue, ParameterValueType valueType, Boolean dynamic,
            Boolean applyAllowed) {
        return new ConfigurationApplyItem(applyId, parameterName, requestedValue, valueType,
                dynamic, applyAllowed);
    }

    public void markBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public void markApplied() {
        if (applyStatus != ConfigurationApplyItemStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING item can be applied. currentStatus=" + applyStatus);
        }

        this.applyStatus = ConfigurationApplyItemStatus.APPLIED;
        this.appliedAt = LocalDateTime.now();
    }

    public void markVerified(String afterValue) {
        if (applyStatus != ConfigurationApplyItemStatus.APPLIED) {
            throw new IllegalStateException(
                    "Only APPLIED item can be verified. currentStatus=" + applyStatus);
        }

        this.afterValue = afterValue;
        this.applyStatus = ConfigurationApplyItemStatus.VERIFIED;
        this.verifiedAt = LocalDateTime.now();
    }

    public void markSkipped(String failureCode, String failureMessage) {
        this.applyStatus = ConfigurationApplyItemStatus.SKIPPED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public void markUnsupported(String failureCode, String failureMessage) {
        this.applyStatus = ConfigurationApplyItemStatus.UNSUPPORTED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public void markFailed(String failureCode, String failureMessage) {
        this.applyStatus = ConfigurationApplyItemStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    private static void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getApplyId() {
        return applyId;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getRequestedValue() {
        return requestedValue;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public ParameterValueType getValueType() {
        return valueType;
    }

    public Boolean getDynamic() {
        return dynamic;
    }

    public Boolean getApplyAllowed() {
        return applyAllowed;
    }

    public ConfigurationApplyItemStatus getApplyStatus() {
        return applyStatus;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
}
