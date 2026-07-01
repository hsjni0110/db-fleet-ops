package com.dbfleetops.database.dto;

import com.dbfleetops.database.domain.DatabaseEngine;

public record DatabaseUpdateRequest(
        String name,
        String host,
        int port,
        String databaseName,
        DatabaseEngine engine,
        String environment,
        String serviceName,
        String owner,
        String description,
        String username,
        String password
) {
}