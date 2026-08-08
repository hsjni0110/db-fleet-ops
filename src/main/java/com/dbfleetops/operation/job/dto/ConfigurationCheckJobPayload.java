package com.dbfleetops.operation.job.dto;

public record ConfigurationCheckJobPayload(Long profileId, String reason, String requestedBy) {
}
