package com.dbfleetops.health.api;

import java.time.OffsetDateTime;

import com.dbfleetops.health.domain.DatabaseErrorCode;
import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.domain.DatabaseStatus;

public record DatabaseHealthResponse(
        String databaseType,
        String host,
        int port,
        DatabaseStatus status,
        long latencyMs,
        OffsetDateTime checkedAt,
        DatabaseErrorCode errorCode,
        String message
) {

    public static DatabaseHealthResponse from(
            DatabaseHealth health
    ) {
        return new DatabaseHealthResponse(
                health.databaseType(),
                health.host(),
                health.port(),
                health.status(),
                health.latencyMs(),
                health.checkedAt(),
                health.errorCode(),
                health.message()
        );
    }
}
