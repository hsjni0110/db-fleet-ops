package com.dbfleetops.operation.task.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ResolveTaskCredentialRequest(@NotBlank String agentToken,
        @Min(1) int executionAttempt) {
}
