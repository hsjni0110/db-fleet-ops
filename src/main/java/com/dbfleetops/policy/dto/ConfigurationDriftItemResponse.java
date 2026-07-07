package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ConfigurationDriftItem;
import com.dbfleetops.policy.domain.ParameterValueType;

import java.time.LocalDateTime;

public record ConfigurationDriftItemResponse(Long driftItemId, Long driftId, String parameterName,
        String expectedValue, String actualValue, ParameterValueType valueType, Boolean required,
        Boolean dynamic, Boolean applyAllowed, ComplianceStatus complianceStatus, String message,
        LocalDateTime createdAt) {

    public static ConfigurationDriftItemResponse from(ConfigurationDriftItem item) {
        return new ConfigurationDriftItemResponse(item.getId(), item.getDriftId(),
                item.getParameterName(), item.getExpectedValue(), item.getActualValue(),
                item.getValueType(), item.getRequired(), item.getDynamic(), item.getApplyAllowed(),
                item.getComplianceStatus(), item.getMessage(), item.getCreatedAt());
    }
}
