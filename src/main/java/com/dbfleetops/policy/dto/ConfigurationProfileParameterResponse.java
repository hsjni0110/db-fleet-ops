package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.domain.ParameterValueType;

import java.time.LocalDateTime;

public record ConfigurationProfileParameterResponse(Long parameterId, Long profileId,
        String parameterName, String expectedValue, ParameterValueType valueType, Boolean required,
        Boolean dynamic, Boolean applyAllowed, String description, LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ConfigurationProfileParameterResponse from(
            ConfigurationProfileParameter parameter) {
        return new ConfigurationProfileParameterResponse(parameter.getId(),
                parameter.getProfileId(), parameter.getParameterName(),
                parameter.getExpectedValue(), parameter.getValueType(), parameter.getRequired(),
                parameter.getDynamic(), parameter.getApplyAllowed(), parameter.getDescription(),
                parameter.getCreatedAt(), parameter.getUpdatedAt());
    }
}
