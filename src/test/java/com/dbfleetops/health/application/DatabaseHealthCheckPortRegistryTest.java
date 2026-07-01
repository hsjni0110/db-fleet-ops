package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.health.domain.HealthStatus;
import com.dbfleetops.health.port.DatabaseHealthCheckPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseHealthCheckPortRegistryTest {

    @Test
    void getPortReturnsPortMatchingEngine() {
        DatabaseHealthCheckPort mysqlPort =
                new StubHealthCheckPort(DatabaseEngine.MYSQL);

        DatabaseHealthCheckPortRegistry registry =
                new DatabaseHealthCheckPortRegistry(
                        List.of(mysqlPort)
                );

        DatabaseHealthCheckPort result =
                registry.getPort(DatabaseEngine.MYSQL);

        assertThat(result)
                .isSameAs(mysqlPort);
    }

    @Test
    void getPortThrowsExceptionWhenEngineIsNotSupported() {
        DatabaseHealthCheckPort mysqlPort =
                new StubHealthCheckPort(DatabaseEngine.MYSQL);

        DatabaseHealthCheckPortRegistry registry =
                new DatabaseHealthCheckPortRegistry(
                        List.of(mysqlPort)
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.getPort(DatabaseEngine.POSTGRESQL)
                );

        assertThat(exception.getMessage())
                .contains("Unsupported database engine for health check");
    }

    private record StubHealthCheckPort(
            DatabaseEngine engine
    ) implements DatabaseHealthCheckPort {

        @Override
        public DatabaseEngine supports() {
            return engine;
        }

        @Override
        public HealthCheckResult check(
                ManagedDatabase database,
                DatabaseCredential credential
        ) {
            return new HealthCheckResult(
                    HealthStatus.UNKNOWN,
                    false,
                    0L,
                    "stub"
            );
        }
    }
}