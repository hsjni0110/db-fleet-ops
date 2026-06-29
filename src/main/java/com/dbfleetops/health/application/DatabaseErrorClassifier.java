package com.dbfleetops.health.application;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

import org.springframework.stereotype.Component;

import com.dbfleetops.health.domain.DatabaseErrorCode;

@Component
public class DatabaseErrorClassifier {

    private static final int MYSQL_ACCESS_DENIED_ERROR_CODE = 1045;
    private static final String SQL_STATE_INVALID_AUTHORIZATION = "28000";
    private static final String SQL_STATE_CONNECTION_EXCEPTION_PREFIX = "08";

    public DatabaseErrorCode classify(SQLException exception) {
        if (isAuthenticationFailure(exception)) {
            return DatabaseErrorCode.AUTHENTICATION_FAILED;
        }

        Throwable current = exception;

        while (current != null) {
            DatabaseErrorCode matched = classifyThrowable(current);

            if (matched != null) {
                return matched;
            }

            current = current.getCause();
        }

        if (isConnectionSqlState(exception)) {
            return DatabaseErrorCode.CONNECTION_REFUSED;
        }

        return DatabaseErrorCode.UNKNOWN_ERROR;
    }

    public String messageFor(DatabaseErrorCode errorCode) {
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

    private boolean isAuthenticationFailure(SQLException exception) {
        return exception.getErrorCode() == MYSQL_ACCESS_DENIED_ERROR_CODE
                || SQL_STATE_INVALID_AUTHORIZATION.equals(
                        exception.getSQLState()
                );
    }

    private boolean isConnectionSqlState(SQLException exception) {
        String sqlState = exception.getSQLState();

        return sqlState != null
                && sqlState.startsWith(
                        SQL_STATE_CONNECTION_EXCEPTION_PREFIX
                );
    }

    private DatabaseErrorCode classifyThrowable(Throwable throwable) {
        if (throwable instanceof UnknownHostException) {
            return DatabaseErrorCode.UNKNOWN_HOST;
        }

        if (throwable instanceof SocketTimeoutException) {
            return DatabaseErrorCode.CONNECTION_TIMEOUT;
        }

        if (throwable instanceof ConnectException) {
            return DatabaseErrorCode.CONNECTION_REFUSED;
        }

        return null;
    }
}