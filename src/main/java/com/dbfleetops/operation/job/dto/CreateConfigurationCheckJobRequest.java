package com.dbfleetops.operation.job.dto;

public record CreateConfigurationCheckJobRequest(Long profileId, String requestedBy,
        String reason) {
}
