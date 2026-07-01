package com.dbfleetops.health.port;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.health.domain.HealthStatus;

public interface DatabaseHealthCheckPort {

    DatabaseEngine supports();

    HealthCheckResult check(
            ManagedDatabase database,
            DatabaseCredential credential
    );

    record HealthCheckResult(
            HealthStatus status,
            boolean connectionSuccess,
            long responseTimeMs,
            String message
    ) {
    }
}