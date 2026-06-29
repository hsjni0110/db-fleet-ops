package com.dbfleetops.adapter.mysql;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.dbfleetops.config.TargetDatabaseProperties;
import com.dbfleetops.health.domain.DatabaseErrorCode;
import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.port.DatabaseHealthProbe;


@Component
public class MySqlDatabaseHealthProbe implements DatabaseHealthProbe {

    private static final String DATABASE_TYPE = "MySQL";
    private static final String VALIDATION_QUERY = "SELECT 1";
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final TargetDatabaseProperties properties;

    public MySqlDatabaseHealthProbe(TargetDatabaseProperties targetDatabaseProperties) {
        this.properties = targetDatabaseProperties;
    }

    @Override
    public DatabaseHealth check() {
        long startedAt = System.nanoTime();

        try (
            Connection connection = DriverManager.getConnection(
                buildJdbcUrl(),
                properties.username(),
                properties.password()
            );
            Statement statement = connection.createStatement();
        ) {
            statement.execute(VALIDATION_QUERY);

            long latencyMs = elapsedMillis(startedAt);

            return DatabaseHealth.up(
                DATABASE_TYPE, 
                properties.host(),
                properties.port(), 
                latencyMs,
                OffsetDateTime.now(DEFAULT_ZONE_ID)
            );
        } catch (SQLException exception) {
            long latencyMs = elapsedMillis(startedAt);
            DatabaseErrorCode errorCode = classify(exception);

            return DatabaseHealth.down(
                    DATABASE_TYPE,
                    properties.host(),
                    properties.port(),
                    latencyMs,
                    OffsetDateTime.now(DEFAULT_ZONE_ID),
                    errorCode,
                    messageFor(errorCode)
            );
        }
    }
    private String buildJdbcUrl() {

        return String.format(
                "jdbc:mysql://%s:%d/%s"
                        + "?connectTimeout=%d"
                        + "&socketTimeout=%d"
                        + "&useSSL=false"
                        + "&allowPublicKeyRetrieval=true",
                properties.host(),
                properties.port(),
                properties.database(),
                properties.connectTimeout().toMillis(),
                properties.socketTimeout().toMillis()
        );
    }

    private long elapsedMillis(long startedAt) {

        return (System.nanoTime() - startedAt) / 1_000_000;

    }

    private DatabaseErrorCode classify(SQLException exception) {

        if (isAuthenticationFailure(exception)) {
            return DatabaseErrorCode.AUTHENTICATION_FAILED;
        }

        Throwable rootCause = findRootCause(exception);

        if (rootCause instanceof UnknownHostException) {
            return DatabaseErrorCode.UNKNOWN_HOST;
        }

        if (rootCause instanceof SocketTimeoutException) {
            return DatabaseErrorCode.CONNECTION_TIMEOUT;
        }

        if (rootCause instanceof ConnectException) {
            return DatabaseErrorCode.CONNECTION_REFUSED;
        }

        return DatabaseErrorCode.UNKNOWN_ERROR;
    }

    private boolean isAuthenticationFailure(SQLException exception) {

        return exception.getErrorCode() == 1045
                || "28000".equals(exception.getSQLState());
    }

    private Throwable findRootCause(Throwable throwable) {

        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String messageFor(DatabaseErrorCode errorCode) {

        return switch (errorCode) {

            case AUTHENTICATION_FAILED ->
                    "Database authentication failed.";
            case CONNECTION_REFUSED ->
                    "Database connection was refused.";
            case CONNECTION_TIMEOUT ->
                    "Database connection timed out.";
            case UNKNOWN_HOST ->
                    "Database host could not be resolved.";
            case UNKNOWN_ERROR ->
                    "Unknown database connection error occurred.";
        };
    }
}