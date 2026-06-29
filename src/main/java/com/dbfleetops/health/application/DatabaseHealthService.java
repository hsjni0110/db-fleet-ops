package com.dbfleetops.health.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.domain.DatabaseStatus;
import com.dbfleetops.health.port.DatabaseHealthProbe;

@Service
public class DatabaseHealthService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DatabaseHealthService.class
            );

    private final DatabaseHealthProbe databaseHealthProbe;

    public DatabaseHealthService(
            DatabaseHealthProbe databaseHealthProbe
    ) {
        this.databaseHealthProbe = databaseHealthProbe;
    }

    public DatabaseHealth checkDefaultDatabase() {
        DatabaseHealth health =
                databaseHealthProbe.check();

        logHealthResult(health);

        return health;
    }

    private void logHealthResult(
            DatabaseHealth health
    ) {
        String errorCode =
                health.errorCode() == null
                        ? "NONE"
                        : health.errorCode().name();

        if (health.status() == DatabaseStatus.DOWN) {
            log.warn(
                    "database_health_checked"
                            + " databaseType={}"
                            + " host={}"
                            + " port={}"
                            + " status={}"
                            + " latencyMs={}"
                            + " errorCode={}",
                    health.databaseType(),
                    health.host(),
                    health.port(),
                    health.status(),
                    health.latencyMs(),
                    errorCode
            );

            return;
        }

        log.info(
                "database_health_checked"
                        + " databaseType={}"
                        + " host={}"
                        + " port={}"
                        + " status={}"
                        + " latencyMs={}"
                        + " errorCode={}",
                health.databaseType(),
                health.host(),
                health.port(),
                health.status(),
                health.latencyMs(),
                errorCode
        );
    }
}