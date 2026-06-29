package com.dbfleetops.adapter.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.dbfleetops.config.TargetDatabaseProperties;
import com.dbfleetops.health.application.DatabaseErrorClassifier;
import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.domain.DatabaseStatus;

@Tag("integration")
class MySqlDatabaseHealthProbeIntegrationTest {

    @Test
    void returnsUpWhenMySqlIsAvailable() {
        TargetDatabaseProperties properties =
                new TargetDatabaseProperties(
                        environmentOrDefault(
                                "DB_TARGET_HOST",
                                "127.0.0.1"
                        ),
                        integerEnvironmentOrDefault(
                                "DB_TARGET_PORT",
                                3306
                        ),
                        environmentOrDefault(
                                "DB_TARGET_NAME",
                                "dbops_target"
                        ),
                        environmentOrDefault(
                                "DB_TARGET_USERNAME",
                                "db_monitor"
                        ),
                        requiredEnvironment(
                                "DB_TARGET_PASSWORD"
                        ),
                        durationEnvironmentOrDefault(
                                "DB_CONNECT_TIMEOUT",
                                Duration.ofSeconds(2)
                        ),
                        durationEnvironmentOrDefault(
                                "DB_SOCKET_TIMEOUT",
                                Duration.ofSeconds(2)
                        )
                );

        DatabaseErrorClassifier classifier =
                new DatabaseErrorClassifier();

        MySqlDatabaseHealthProbe probe =
                new MySqlDatabaseHealthProbe(
                        properties,
                        classifier
                );

        DatabaseHealth result = probe.check();

        assertThat(result.status())
                .as(
                        "errorCode=%s, message=%s",
                        result.errorCode(),
                        result.message()
                )
                .isEqualTo(DatabaseStatus.UP);

        assertThat(result.databaseType())
                .isEqualTo("MYSQL");

        assertThat(result.errorCode())
                .isNull();

        assertThat(result.latencyMs())
                .isGreaterThanOrEqualTo(0L);
    }

    private String requiredEnvironment(
            String name
    ) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable is missing: "
                            + name
                            + ". Run 'set -a; source .env; set +a' "
                            + "before integrationTest."
            );
        }

        return value;
    }

    private String environmentOrDefault(
            String name,
            String defaultValue
    ) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    private int integerEnvironmentOrDefault(
            String name,
            int defaultValue
    ) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }

    private Duration durationEnvironmentOrDefault(
            String name,
            Duration defaultValue
    ) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Duration.parse(
                normalizeDuration(value)
        );
    }

    private String normalizeDuration(
            String value
    ) {
        String normalized = value.trim().toLowerCase();

        if (normalized.endsWith("ms")) {
            String number = normalized.substring(
                    0,
                    normalized.length() - 2
            );

            return "PT" + number + "M";
        }

        if (normalized.endsWith("s")) {
            String number = normalized.substring(
                    0,
                    normalized.length() - 1
            );

            return "PT" + number + "S";
        }

        return value;
    }
}