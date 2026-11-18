package com.dbfleetops.backup.dto;

public record BusinessRuleResultPayload(String code, String name, String status,
        Long violationCount, Long durationMillis, String message) {}
