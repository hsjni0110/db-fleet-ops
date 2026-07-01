package com.dbfleetops.health.infra;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.health.application.DatabaseHealthAdapter;
import com.dbfleetops.health.domain.HealthStatus;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Component
public class MySqlHealthAdapter implements DatabaseHealthAdapter {

    @Override
    public DatabaseEngine supports() {
        return DatabaseEngine.MYSQL;
    }

    @Override
    public HealthCheckResult check(ManagedDatabase database, DatabaseCredential credential) {
        long start = System.currentTimeMillis();

        String jdbcUrl = "jdbc:mysql://"
                + database.getHost()
                + ":"
                + database.getPort()
                + "/"
                + database.getDatabaseName()
                + "?connectTimeout=3000&socketTimeout=3000";

        try (
                Connection connection = DriverManager.getConnection(
                        jdbcUrl,
                        credential.getUsername(),
                        credential.getPassword()
                );
                Statement statement = connection.createStatement()
        ) {
            statement.execute("SELECT 1");

            long responseTimeMs = System.currentTimeMillis() - start;

            HealthStatus status = responseTimeMs > 1000
                    ? HealthStatus.DEGRADED
                    : HealthStatus.HEALTHY;

            return new HealthCheckResult(
                    status,
                    true,
                    responseTimeMs,
                    "MySQL connection check succeeded."
            );
        } catch (Exception e) {
            long responseTimeMs = System.currentTimeMillis() - start;

            return new HealthCheckResult(
                    HealthStatus.CRITICAL,
                    false,
                    responseTimeMs,
                    e.getMessage()
            );
        }
    }
}