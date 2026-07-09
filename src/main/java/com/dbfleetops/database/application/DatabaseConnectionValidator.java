package com.dbfleetops.database.application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.stereotype.Component;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.dto.DatabaseCreateRequest;
import com.dbfleetops.database.dto.DatabaseUpdateRequest;
import com.dbfleetops.database.exception.DatabaseConnectionValidationException;

@Component
public class DatabaseConnectionValidator {

    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    public void validate(DatabaseCreateRequest request) {
        validate(request.engine(), request.host(), request.port(), request.databaseName(),
                request.username(), request.password());
    }

    public void validate(DatabaseUpdateRequest request) {
        validate(request.engine(), request.host(), request.port(), request.databaseName(),
                request.username(), request.password());
    }

    private void validate(DatabaseEngine engine, String host, int port, String databaseName,
            String username, String password) {
        validateRequiredFields(engine, host, port, databaseName, username, password);

        if (engine != DatabaseEngine.MYSQL) {
            throw new IllegalArgumentException(
                    "Unsupported database engine for connection validation. engine=" + engine);
        }

        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);

        String jdbcUrl = buildJdbcUrl(engine, host, port, databaseName);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            if (!connection.isValid(LOGIN_TIMEOUT_SECONDS)) {
                throw new DatabaseConnectionValidationException(
                        "존재하지 않거나 접근할 수 없는 DB입니다. host, port, databaseName, username, password를 확인하세요.");
            }
        } catch (SQLException exception) {
            throw new DatabaseConnectionValidationException(
                    "존재하지 않거나 접근할 수 없는 DB입니다. host, port, databaseName, username, password를 확인하세요.",
                    exception);
        }
    }

    private void validateRequiredFields(DatabaseEngine engine, String host, int port,
            String databaseName, String username, String password) {
        if (engine == null) {
            throw new IllegalArgumentException("Database engine is required.");
        }

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Database host is required.");
        }

        if (port <= 0) {
            throw new IllegalArgumentException("Database port must be greater than 0.");
        }

        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalArgumentException("Database name is required.");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Database username is required.");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Database password is required.");
        }
    }

    private String buildJdbcUrl(DatabaseEngine engine, String host, int port, String databaseName) {
        if (engine == DatabaseEngine.MYSQL) {
            return "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
                    .formatted(host, port, databaseName);
        }

        throw new IllegalArgumentException(
                "Unsupported database engine for connection validation. engine=" + engine);
    }
}
