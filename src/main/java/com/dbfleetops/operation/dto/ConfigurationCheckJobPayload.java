package com.dbfleetops.operation.dto;

public record ConfigurationCheckJobPayload(Long profileId, String reason, String requestedBy) {
}
