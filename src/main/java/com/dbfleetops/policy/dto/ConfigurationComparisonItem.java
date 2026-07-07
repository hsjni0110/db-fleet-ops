package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ParameterValueType;

public record ConfigurationComparisonItem(String parameterName, String expectedValue,
        String actualValue, ParameterValueType valueType, Boolean required, Boolean dynamic,
        Boolean applyAllowed, ComplianceStatus complianceStatus, String message) {
}
