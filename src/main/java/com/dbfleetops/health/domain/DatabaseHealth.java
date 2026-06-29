package com.dbfleetops.health.domain;

import java.time.OffsetDateTime;

public record DatabaseHealth(
    String databaseType,
    String host,
    int port,
    DatabaseStatus status,
    long latencyMs,
    OffsetDateTime checkedAt,
    DatabaseErrorCode errorCode,
    String message
) {

    public static DatabaseHealth up(
        String databaseType,
        String host,
        int port,
        long latencyMs,
        OffsetDateTime checkedAt
    ) {
        return new DatabaseHealth(
            databaseType, 
            host, 
            port, 
            DatabaseStatus.UP, 
            latencyMs, 
            checkedAt, 
            null, 
            "Database connection is available."
        );
    }

    public static DatabaseHealth down(
        String databaseType,
        String host,
        int port,
        long latencyMs,
        OffsetDateTime checkedAt,
        DatabaseErrorCode errorCode,
        String message

    ) {
        return new DatabaseHealth(
            databaseType,
            host,
            port,
            DatabaseStatus.DOWN,
            latencyMs,
            checkedAt,
            errorCode,
            message
        );
    }
}
