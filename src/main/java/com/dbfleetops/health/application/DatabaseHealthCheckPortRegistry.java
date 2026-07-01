package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.port.DatabaseHealthCheckPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseHealthCheckPortRegistry {

    private final List<DatabaseHealthCheckPort> ports;

    public DatabaseHealthCheckPortRegistry(
            List<DatabaseHealthCheckPort> ports
    ) {
        this.ports = ports;
    }

    public DatabaseHealthCheckPort getPort(
            DatabaseEngine engine
    ) {
        return ports.stream()
                .filter(port -> port.supports() == engine)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported database engine for health check: " + engine
                ));
    }
}