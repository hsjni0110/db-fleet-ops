package com.dbfleetops.health.dto;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.domain.DatabaseVersionInfo;

public record DatabaseVersionResponse(
        Long databaseId,
        DatabaseEngine engine,
        String version
) {
    public static DatabaseVersionResponse from(
            Long databaseId,
            DatabaseEngine engine,
            DatabaseVersionInfo info
    ) {
        return new DatabaseVersionResponse(
                databaseId,
                engine,
                info.version()
        );
    }
}