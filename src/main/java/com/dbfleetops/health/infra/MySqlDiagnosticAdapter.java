package com.dbfleetops.health.infra;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.health.domain.ConnectionSummary;
import com.dbfleetops.health.domain.DatabaseUptimeInfo;
import com.dbfleetops.health.domain.DatabaseVersionInfo;
import com.dbfleetops.health.domain.LockWaitInfo;
import com.dbfleetops.health.domain.LongTransactionInfo;
import com.dbfleetops.health.domain.SessionInfo;
import com.dbfleetops.health.domain.SlowQueryInfo;
import com.dbfleetops.health.port.DatabaseDiagnosticPort;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@Component
public class MySqlDiagnosticAdapter implements DatabaseDiagnosticPort {

    @Override
    public DatabaseEngine supports() {
        return DatabaseEngine.MYSQL;
    }

    @Override
    public DatabaseVersionInfo getVersion(
            ManagedDatabase database,
            DatabaseCredential credential
    ) {
        try (
                Connection connection = getConnection(database, credential);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT VERSION()")
        ) {
            if (resultSet.next()) {
                return new DatabaseVersionInfo(
                        resultSet.getString(1)
                );
            }

            throw new IllegalStateException("MySQL version result is empty.");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to get MySQL version.",
                    e
            );
        }
    }

    @Override
    public DatabaseUptimeInfo getUptime(
            ManagedDatabase database,
            DatabaseCredential credential
    ) {
        try (
                Connection connection = getConnection(database, credential);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SHOW GLOBAL STATUS LIKE 'Uptime'"
                )
        ) {
            if (resultSet.next()) {
                return new DatabaseUptimeInfo(
                        Long.parseLong(resultSet.getString("Value"))
                );
            }

            throw new IllegalStateException("MySQL uptime result is empty.");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to get MySQL uptime.",
                    e
            );
        }
    }

    @Override
    public ConnectionSummary getConnectionSummary(
            ManagedDatabase database,
            DatabaseCredential credential
    ) {
        try (
                Connection connection = getConnection(database, credential);
                Statement statement = connection.createStatement()
        ) {
            int currentConnections = getStatusValue(
                    statement,
                    "Threads_connected"
            );
    
            int runningConnections = getStatusValue(
                    statement,
                    "Threads_running"
            );
    
            int maxConnections = getVariableValue(
                    statement,
                    "max_connections"
            );
    
            double usagePercent =
                    calculateUsagePercent(
                            currentConnections,
                            maxConnections
                    );
    
            return new ConnectionSummary(
                    currentConnections,
                    runningConnections,
                    maxConnections,
                    usagePercent
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to get MySQL connection summary.",
                    e
            );
        }
    }

    private int getStatusValue(
        Statement statement,
        String name
    ) throws Exception {
        try (
                ResultSet resultSet = statement.executeQuery(
                        "SHOW GLOBAL STATUS LIKE '" + name + "'"
                )
        ) {
            if (resultSet.next()) {
                return Integer.parseInt(
                        resultSet.getString("Value")
                );
            }

            throw new IllegalStateException(
                    "MySQL status value not found. name=" + name
            );
        }
    }

    private int getVariableValue(
            Statement statement,
            String name
    ) throws Exception {
        try (
                ResultSet resultSet = statement.executeQuery(
                        "SHOW GLOBAL VARIABLES LIKE '" + name + "'"
                )
        ) {
            if (resultSet.next()) {
                return Integer.parseInt(
                        resultSet.getString("Value")
                );
            }

            throw new IllegalStateException(
                    "MySQL variable value not found. name=" + name
            );
        }
    }

    private double calculateUsagePercent(
            int currentConnections,
            int maxConnections
    ) {
        if (maxConnections <= 0) {
            return 0.0;
        }

        return Math.round(
                ((double) currentConnections / maxConnections * 100.0) * 100.0
        ) / 100.0;
    }

    @Override
    public List<SessionInfo> getSessions(
            ManagedDatabase database,
            DatabaseCredential credential
    ) {
        String sql = """
                SELECT
                    ID,
                    USER,
                    HOST,
                    DB,
                    COMMAND,
                    TIME,
                    STATE,
                    INFO
                FROM information_schema.PROCESSLIST
                ORDER BY TIME DESC
                """;

        try (
                Connection connection = getConnection(database, credential);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            List<SessionInfo> sessions =
                    new java.util.ArrayList<>();

            while (resultSet.next()) {
                sessions.add(
                        new SessionInfo(
                                resultSet.getLong("ID"),
                                resultSet.getString("USER"),
                                resultSet.getString("HOST"),
                                resultSet.getString("DB"),
                                resultSet.getString("COMMAND"),
                                resultSet.getLong("TIME"),
                                resultSet.getString("STATE"),
                                preview(
                                        resultSet.getString("INFO")
                                )
                        )
                );
            }

            return sessions;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to get MySQL sessions.",
                    e
            );
        }
    }

    private String preview(
        String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized =
                value.replaceAll("\\s+", " ").trim();

        int maxLength = 300;

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength);
    }

    @Override
    public List<LongTransactionInfo> getLongTransactions(
            ManagedDatabase database,
            DatabaseCredential credential
    ) {
        throw new UnsupportedOperationException(
                "Long transaction diagnostic is not implemented yet."
        );
    }

    @Override
    public List<LockWaitInfo> getLockWaits(
            ManagedDatabase database,
            DatabaseCredential credential
    ) {
        throw new UnsupportedOperationException(
                "Lock wait diagnostic is not implemented yet."
        );
    }

    @Override
    public List<SlowQueryInfo> getSlowQueries(
            ManagedDatabase database,
            DatabaseCredential credential
    ) {
        throw new UnsupportedOperationException(
                "Slow query diagnostic is not implemented yet."
        );
    }

    private Connection getConnection(
            ManagedDatabase database,
            DatabaseCredential credential
    ) throws Exception {
        String jdbcUrl = "jdbc:mysql://"
                + database.getHost()
                + ":"
                + database.getPort()
                + "/"
                + database.getDatabaseName()
                + "?connectTimeout=3000&socketTimeout=3000";

        return DriverManager.getConnection(
                jdbcUrl,
                credential.getUsername(),
                credential.getPassword()
        );
    }
}