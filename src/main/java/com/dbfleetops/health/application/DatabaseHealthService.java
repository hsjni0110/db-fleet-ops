package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.domain.DatabaseHealthResult;
import com.dbfleetops.health.domain.DatabaseStatus;
import com.dbfleetops.health.dto.DatabaseHealthResponse;
import com.dbfleetops.health.infra.DatabaseHealthResultRepository;
import com.dbfleetops.health.port.DatabaseHealthProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseHealthService {

    private static final Logger log =
            LoggerFactory.getLogger(DatabaseHealthService.class);

    private final DatabaseHealthProbe databaseHealthProbe;
    private final ManagedDatabaseRepository databaseRepository;
    private final DatabaseCredentialRepository credentialRepository;
    private final DatabaseHealthAdapterFactory adapterFactory;
    private final DatabaseHealthResultRepository healthResultRepository;

    public DatabaseHealthService(
            DatabaseHealthProbe databaseHealthProbe,
            ManagedDatabaseRepository databaseRepository,
            DatabaseCredentialRepository credentialRepository,
            DatabaseHealthAdapterFactory adapterFactory,
            DatabaseHealthResultRepository healthResultRepository
    ) {
        this.databaseHealthProbe = databaseHealthProbe;
        this.databaseRepository = databaseRepository;
        this.credentialRepository = credentialRepository;
        this.adapterFactory = adapterFactory;
        this.healthResultRepository = healthResultRepository;
    }

    public DatabaseHealth checkDefaultDatabase() {
        DatabaseHealth health =
                databaseHealthProbe.check();

        logHealthResult(health);

        return health;
    }

    @Transactional
    public DatabaseHealthResponse check(Long databaseId) {
        ManagedDatabase database = databaseRepository.findById(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Database not found. databaseId=" + databaseId
                ));

        if (!database.isActive()) {
            throw new IllegalStateException(
                    "Inactive database cannot be checked. databaseId=" + databaseId
            );
        }

        DatabaseCredential credential = credentialRepository.findByDatabaseId(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Credential not found. databaseId=" + databaseId
                ));

        DatabaseHealthAdapter adapter =
                adapterFactory.getAdapter(database.getEngine());

        DatabaseHealthAdapter.HealthCheckResult checkResult =
                adapter.check(database, credential);

        DatabaseHealthResult result = new DatabaseHealthResult(
                databaseId,
                checkResult.status(),
                checkResult.connectionSuccess(),
                checkResult.responseTimeMs(),
                checkResult.message()
        );

        DatabaseHealthResult savedResult =
                healthResultRepository.save(result);

        return DatabaseHealthResponse.from(savedResult);
    }

    private void logHealthResult(DatabaseHealth health) {
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