package com.dbfleetops.adapter.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.dbfleetops.config.TargetDatabaseProperties;
import com.dbfleetops.health.application.DatabaseErrorClassifier;
import com.dbfleetops.health.domain.DatabaseErrorCode;
import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.port.DatabaseHealthProbe;

@Component
public class MySqlDatabaseHealthProbe implements DatabaseHealthProbe {

    private static final String DATABASE_TYPE = "MYSQL";
    private static final String VALIDATION_QUERY = "SELECT 1";
    private static final ZoneId DEFAULT_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private final TargetDatabaseProperties properties;
    private final DatabaseErrorClassifier errorClassifier;

    public MySqlDatabaseHealthProbe(
            TargetDatabaseProperties properties,
            DatabaseErrorClassifier errorClassifier
    ) {
        this.properties = properties;
        this.errorClassifier = errorClassifier;
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
                Statement statement = connection.createStatement()
        ) {
            statement.execute(VALIDATION_QUERY);

            return DatabaseHealth.up(
                    DATABASE_TYPE,
                    properties.host(),
                    properties.port(),
                    elapsedMillis(startedAt),
                    OffsetDateTime.now(DEFAULT_ZONE_ID)
            );

        } catch (SQLException exception) {
            DatabaseErrorCode errorCode =
                    errorClassifier.classify(exception);

            return DatabaseHealth.down(
                    DATABASE_TYPE,
                    properties.host(),
                    properties.port(),
                    elapsedMillis(startedAt),
                    OffsetDateTime.now(DEFAULT_ZONE_ID),
                    errorCode,
                    errorClassifier.messageFor(errorCode)
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
}