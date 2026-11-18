package com.dbfleetops.backup.dto;

import java.util.List;

public record BusinessVerificationResultPayload(String profileKey, String status,
        Integer checkedRuleCount, Integer failedRuleCount,
        List<BusinessRuleResultPayload> ruleResults) {}
