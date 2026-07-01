package com.dbfleetops.health.domain;

public record ConnectionSummary(
        int currentConnections,
        int runningConnections,
        int maxConnections,
        double usagePercent
) {
}