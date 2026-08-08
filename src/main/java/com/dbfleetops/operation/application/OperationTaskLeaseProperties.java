package com.dbfleetops.operation.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "db-fleetops.task-lease")
public record OperationTaskLeaseProperties(
        @NotNull Duration duration,
        @NotNull Duration renewalInterval,
        @NotNull Duration expirationCheckInterval,
        @Min(1) int maximumAttempts,
        @Min(1) int expirationBatchSize) {
}
