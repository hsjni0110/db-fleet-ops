package com.dbfleetops.operation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RenewOperationTaskLeaseRequest(@NotBlank String agentToken,
        @Min(1) int executionAttempt) {
}
