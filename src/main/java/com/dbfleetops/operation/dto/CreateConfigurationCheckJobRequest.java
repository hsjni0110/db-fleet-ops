package com.dbfleetops.operation.dto;

public record CreateConfigurationCheckJobRequest(Long profileId, String requestedBy,
        String reason) {
}
