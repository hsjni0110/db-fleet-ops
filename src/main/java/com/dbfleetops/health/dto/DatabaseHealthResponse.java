package com.dbfleetops.health.dto;

import com.dbfleetops.health.domain.DatabaseHealthResult;
import com.dbfleetops.health.domain.HealthStatus;

import java.time.LocalDateTime;

public record DatabaseHealthResponse(
        Long databaseId,
        HealthStatus status,
        boolean connectionSuccess,
        long responseTimeMs,
        String message,
        LocalDateTime checkedAt
) {
    public static DatabaseHealthResponse from(DatabaseHealthResult result) {
        return new DatabaseHealthResponse(
                result.getDatabaseId(),
                result.getStatus(),
                result.isConnectionSuccess(),
                result.getResponseTimeMs(),
                result.getMessage(),
                result.getCheckedAt()
        );
    }
}