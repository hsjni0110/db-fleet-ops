package com.dbfleetops.operation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record FailOperationTaskRequest(@NotBlank String agentToken,
        @Min(1) int executionAttempt, @NotBlank String errorCode,
        @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        String resultReportId,
        String errorMessage) {
    public FailOperationTaskRequest(String agentToken, String errorCode, String errorMessage) {
        this(agentToken, 1, errorCode, UUID.randomUUID().toString(), errorMessage);
    }
}
