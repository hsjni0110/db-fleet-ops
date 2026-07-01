package com.dbfleetops.health.dto;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.domain.ConnectionSummary;

public record ConnectionSummaryResponse(
        Long databaseId,
        DatabaseEngine engine,
        int currentConnections,
        int runningConnections,
        int maxConnections,
        double usagePercent
) {
    public static ConnectionSummaryResponse from(
            Long databaseId,
            DatabaseEngine engine,
            ConnectionSummary summary
    ) {
        return new ConnectionSummaryResponse(
                databaseId,
                engine,
                summary.currentConnections(),
                summary.runningConnections(),
                summary.maxConnections(),
                summary.usagePercent()
        );
    }
}