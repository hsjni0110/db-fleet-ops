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
        throw new UnsupportedOperationException(
                "Connection summary diagnostic is not implemented yet."
        );
    }

    @Override
    public List<SessionInfo> getSessions(
            ManagedDatabase database,
            DatabaseCredential credential
    ) {
        throw new UnsupportedOperationException(
                "Session diagnostic is not implemented yet."
        );
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