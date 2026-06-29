package com.dbfleetops.health.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dbfleetops.health.domain.DatabaseErrorCode;

class DatabaseErrorClassifierTest {

    private DatabaseErrorClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new DatabaseErrorClassifier();
    }

    @Test
    void classifiesAuthenticationFailureByVendorErrorCode() {
        SQLException exception = new SQLException(
                "Access denied",
                "28000",
                1045
        );

        DatabaseErrorCode result =
                classifier.classify(exception);

        assertThat(result)
                .isEqualTo(
                        DatabaseErrorCode.AUTHENTICATION_FAILED
                );
    }

    @Test
    void classifiesAuthenticationFailureBySqlState() {
        SQLException exception = new SQLException(
                "Authentication failed",
                "28000",
                0
        );

        DatabaseErrorCode result =
                classifier.classify(exception);

        assertThat(result)
                .isEqualTo(
                        DatabaseErrorCode.AUTHENTICATION_FAILED
                );
    }

    @Test
    void classifiesUnknownHost() {
        SQLException exception = new SQLException(
                "Connection failed",
                new UnknownHostException(
                        "unknown-db-host"
                )
        );

        DatabaseErrorCode result =
                classifier.classify(exception);

        assertThat(result)
                .isEqualTo(
                        DatabaseErrorCode.UNKNOWN_HOST
                );
    }

    @Test
    void classifiesConnectionTimeout() {
        SQLException exception = new SQLException(
                "Connection timed out",
                new SocketTimeoutException(
                        "connect timed out"
                )
        );

        DatabaseErrorCode result =
                classifier.classify(exception);

        assertThat(result)
                .isEqualTo(
                        DatabaseErrorCode.CONNECTION_TIMEOUT
                );
    }

    @Test
    void classifiesConnectionRefused() {
        SQLException exception = new SQLException(
                "Connection refused",
                new ConnectException(
                        "Connection refused"
                )
        );

        DatabaseErrorCode result =
                classifier.classify(exception);

        assertThat(result)
                .isEqualTo(
                        DatabaseErrorCode.CONNECTION_REFUSED
                );
    }

    @Test
    void searchesEntireExceptionCauseChain() {
        ConnectException connectException =
                new ConnectException(
                        "Connection refused"
                );

        RuntimeException middleException =
                new RuntimeException(
                        "Driver wrapper",
                        connectException
                );

        SQLException exception = new SQLException(
                "Communication failure",
                middleException
        );

        DatabaseErrorCode result =
                classifier.classify(exception);

        assertThat(result)
                .isEqualTo(
                        DatabaseErrorCode.CONNECTION_REFUSED
                );
    }

    @Test
    void classifiesConnectionSqlStateAsConnectionRefusedFallback() {
        SQLException exception = new SQLException(
                "Communication link failure",
                "08001",
                0
        );

        DatabaseErrorCode result =
                classifier.classify(exception);

        assertThat(result)
                .isEqualTo(
                        DatabaseErrorCode.CONNECTION_REFUSED
                );
    }

    @Test
    void returnsUnknownErrorWhenExceptionCannotBeClassified() {
        SQLException exception = new SQLException(
                "Unexpected SQL error",
                "HY000",
                9999
        );

        DatabaseErrorCode result =
                classifier.classify(exception);

        assertThat(result)
                .isEqualTo(
                        DatabaseErrorCode.UNKNOWN_ERROR
                );
    }

    @Test
    void returnsSafeMessageForAuthenticationFailure() {
        String message = classifier.messageFor(
                DatabaseErrorCode.AUTHENTICATION_FAILED
        );

        assertThat(message)
                .isEqualTo(
                        "Database authentication failed."
                );
    }

    @Test
    void returnsSafeMessageForConnectionRefused() {
        String message = classifier.messageFor(
                DatabaseErrorCode.CONNECTION_REFUSED
        );

        assertThat(message)
                .isEqualTo(
                        "Database connection was refused."
                );
    }
}