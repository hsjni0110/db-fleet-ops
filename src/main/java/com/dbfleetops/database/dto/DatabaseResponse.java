package com.dbfleetops.database.dto;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.DatabaseStatus;
import com.dbfleetops.database.domain.ManagedDatabase;

public record DatabaseResponse(
    Long id,
    String name,
    String host,
    int port,
    String databaseName,
    DatabaseEngine engine,
    DatabaseStatus status,
    String environment,
    String serviceName,
    String owner,
    String description
) {
    public static DatabaseResponse from(ManagedDatabase database) {
        return new DatabaseResponse(
            database.getId(),
            database.getName(),
            database.getHost(),
            database.getPort(),
            database.getDatabaseName(),
            database.getEngine(),
            database.getStatus(),
            database.getEnvironment(),
            database.getServiceName(),
            database.getOwner(),
            database.getDescription()
        );
    }
}