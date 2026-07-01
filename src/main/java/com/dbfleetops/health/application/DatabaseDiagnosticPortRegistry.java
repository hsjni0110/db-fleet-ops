package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.port.DatabaseDiagnosticPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseDiagnosticPortRegistry {

    private final List<DatabaseDiagnosticPort> ports;

    public DatabaseDiagnosticPortRegistry(
            List<DatabaseDiagnosticPort> ports
    ) {
        this.ports = ports;
    }

    public DatabaseDiagnosticPort getPort(
            DatabaseEngine engine
    ) {
        return ports.stream()
                .filter(port -> port.supports() == engine)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported database engine for diagnostics: " + engine
                ));
    }
}