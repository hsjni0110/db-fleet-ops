package com.dbfleetops.operation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CompleteOperationTaskRequest(@NotBlank String agentToken,
        @Min(1) int executionAttempt,
        @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        String resultReportId,
        String resultPayloadJson) {
    public CompleteOperationTaskRequest(String agentToken, String resultPayloadJson) {
        this(agentToken, 1, UUID.randomUUID().toString(), resultPayloadJson);
    }
}
