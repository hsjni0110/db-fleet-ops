package com.dbfleetops.backup.dto;

import com.dbfleetops.backup.domain.BusinessRuleResult;
import com.dbfleetops.backup.domain.BusinessRuleStatus;

public record BusinessRuleResultResponse(String ruleCode, String ruleName,
        BusinessRuleStatus status, Long violationCount, Long durationMillis, String message) {

    public static BusinessRuleResultResponse from(BusinessRuleResult result) {
        return new BusinessRuleResultResponse(result.getRuleCode(), result.getRuleName(),
                result.getStatus(), result.getViolationCount(), result.getDurationMillis(),
                result.getMessage());
    }
}
