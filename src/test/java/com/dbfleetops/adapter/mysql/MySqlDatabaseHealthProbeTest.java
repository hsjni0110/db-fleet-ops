package com.dbfleetops.adapter.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dbfleetops.config.TargetDatabaseProperties;
import com.dbfleetops.health.application.DatabaseErrorClassifier;
import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.domain.DatabaseStatus;

import java.time.Duration;

class MySqlDatabaseHealthProbeTest {

    @Test
    void returnsUpWhenMySqlIsAvailable() {
        TargetDatabaseProperties properties =
            new TargetDatabaseProperties(
                    "localhost",
                    3306,
                    "dbops_target",
                    "db_monitor",
                    "local_monitor_password",
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(2)
            );

            DatabaseErrorClassifier classifier = new DatabaseErrorClassifier();

            MySqlDatabaseHealthProbe probe =
                    new MySqlDatabaseHealthProbe(
                            properties,
                            classifier
                    );

            DatabaseHealth result = probe.check();
            assertThat(result.status())
                    .as("errorCode=%s, message=%s",
                            result.errorCode(),
                            result.message())
                    .isEqualTo(DatabaseStatus.UP);
    }

    @Test
    void returnsDownWhenPortIsClosed() {
        TargetDatabaseProperties properties =
                new TargetDatabaseProperties(
                        "localhost",
                        3307,
                        "dbops_target",
                        "db_monitor",
                        "local_monitor_password",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)
                );

        DatabaseErrorClassifier classifier = new DatabaseErrorClassifier();

        MySqlDatabaseHealthProbe probe =
                new MySqlDatabaseHealthProbe(
                        properties,
                        classifier
                );

        DatabaseHealth result = probe.check();

        assertThat(result.status())
                .isEqualTo(DatabaseStatus.DOWN);

        assertThat(result.errorCode())
                .isNotNull();
    }
}