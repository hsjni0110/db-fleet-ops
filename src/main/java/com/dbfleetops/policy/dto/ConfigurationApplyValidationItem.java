package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ParameterValueType;

public record ConfigurationApplyValidationItem(String parameterName, String targetValue,
        ParameterValueType valueType, Boolean dynamic, Boolean applyAllowed) {
}
