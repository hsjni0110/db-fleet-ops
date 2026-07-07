package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ParameterValueType;

public record AddConfigurationProfileParameterRequest(String parameterName, String expectedValue,
        ParameterValueType valueType, Boolean required, Boolean dynamic, Boolean applyAllowed,
        String description) {
}
