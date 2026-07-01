package com.dbfleetops.health.dto;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.domain.DatabaseUptimeInfo;

public record DatabaseUptimeResponse(
        Long databaseId,
        DatabaseEngine engine,
        long uptimeSeconds
) {
    public static DatabaseUptimeResponse from(
            Long databaseId,
            DatabaseEngine engine,
            DatabaseUptimeInfo info
    ) {
        return new DatabaseUptimeResponse(
                databaseId,
                engine,
                info.uptimeSeconds()
        );
    }
}