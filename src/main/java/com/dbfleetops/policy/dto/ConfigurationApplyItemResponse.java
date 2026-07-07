package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationApplyItemStatus;
import com.dbfleetops.policy.domain.ParameterValueType;

import java.time.LocalDateTime;

public record ConfigurationApplyItemResponse(Long applyItemId, Long applyId, String parameterName,
        String requestedValue, String beforeValue, String afterValue, ParameterValueType valueType,
        Boolean dynamic, Boolean applyAllowed, ConfigurationApplyItemStatus applyStatus,
        String failureCode, String failureMessage, LocalDateTime createdAt, LocalDateTime appliedAt,
        LocalDateTime verifiedAt) {

    public static ConfigurationApplyItemResponse from(ConfigurationApplyItem item) {
        return new ConfigurationApplyItemResponse(item.getId(), item.getApplyId(),
                item.getParameterName(), item.getRequestedValue(), item.getBeforeValue(),
                item.getAfterValue(), item.getValueType(), item.getDynamic(),
                item.getApplyAllowed(), item.getApplyStatus(), item.getFailureCode(),
                item.getFailureMessage(), item.getCreatedAt(), item.getAppliedAt(),
                item.getVerifiedAt());
    }
}
