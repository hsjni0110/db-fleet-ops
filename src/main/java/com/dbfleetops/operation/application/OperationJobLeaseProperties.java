package com.dbfleetops.operation.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "db-fleetops.job-lease")
public record OperationJobLeaseProperties(Duration duration, Duration expirationCheckInterval,
        Duration retryDelay, int expirationBatchSize, boolean reaperEnabled) {
    public OperationJobLeaseProperties {
        if (duration == null || duration.isZero() || duration.isNegative()) duration = Duration.ofSeconds(60);
        if (expirationCheckInterval == null || expirationCheckInterval.isZero()
                || expirationCheckInterval.isNegative()) expirationCheckInterval = Duration.ofSeconds(5);
        if (retryDelay == null || retryDelay.isNegative()) retryDelay = Duration.ofSeconds(30);
        if (expirationBatchSize < 1) expirationBatchSize = 100;
    }
}
