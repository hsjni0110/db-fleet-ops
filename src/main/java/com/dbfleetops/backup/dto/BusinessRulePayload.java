package com.dbfleetops.backup.dto;

public record BusinessRulePayload(String code, String name, String query, Long timeoutMillis) {}
