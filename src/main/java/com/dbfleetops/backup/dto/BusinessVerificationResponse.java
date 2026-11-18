package com.dbfleetops.backup.dto;

import com.dbfleetops.backup.domain.BusinessVerificationStatus;

import java.util.List;

public record BusinessVerificationResponse(String profileKey,
        BusinessVerificationStatus status, Integer checkedRuleCount, Integer failedRuleCount,
        List<BusinessRuleResultResponse> ruleResults) {}
