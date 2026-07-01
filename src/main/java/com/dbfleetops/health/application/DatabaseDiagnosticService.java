package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.health.dto.ConnectionSummaryResponse;
import com.dbfleetops.health.dto.DatabaseUptimeResponse;
import com.dbfleetops.health.dto.DatabaseVersionResponse;
import com.dbfleetops.health.port.DatabaseDiagnosticPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseDiagnosticService {

    private final ManagedDatabaseRepository databaseRepository;
    private final DatabaseCredentialRepository credentialRepository;
    private final DatabaseDiagnosticPortRegistry portRegistry;

    public DatabaseDiagnosticService(
            ManagedDatabaseRepository databaseRepository,
            DatabaseCredentialRepository credentialRepository,
            DatabaseDiagnosticPortRegistry portRegistry
    ) {
        this.databaseRepository = databaseRepository;
        this.credentialRepository = credentialRepository;
        this.portRegistry = portRegistry;
    }

    @Transactional(readOnly = true)
    public DatabaseVersionResponse getVersion(
            Long databaseId
    ) {
        DiagnosticTarget target =
                getTarget(databaseId);

        return DatabaseVersionResponse.from(
                databaseId,
                target.database().getEngine(),
                target.port().getVersion(
                        target.database(),
                        target.credential()
                )
        );
    }

    @Transactional(readOnly = true)
    public DatabaseUptimeResponse getUptime(
            Long databaseId
    ) {
        DiagnosticTarget target =
                getTarget(databaseId);

        return DatabaseUptimeResponse.from(
                databaseId,
                target.database().getEngine(),
                target.port().getUptime(
                        target.database(),
                        target.credential()
                )
        );
    }

    private DiagnosticTarget getTarget(
            Long databaseId
    ) {
        ManagedDatabase database =
                databaseRepository.findById(databaseId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Database not found. databaseId=" + databaseId
                        ));

        if (!database.isActive()) {
            throw new IllegalStateException(
                    "Inactive database cannot be diagnosed. databaseId="
                            + databaseId
            );
        }

        DatabaseCredential credential =
                credentialRepository.findByDatabaseId(databaseId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Credential not found. databaseId=" + databaseId
                        ));

        DatabaseDiagnosticPort port =
                portRegistry.getPort(database.getEngine());

        return new DiagnosticTarget(
                database,
                credential,
                port
        );
    }

    private record DiagnosticTarget(
            ManagedDatabase database,
            DatabaseCredential credential,
            DatabaseDiagnosticPort port
    ) {
    }

    @Transactional(readOnly = true)
    public ConnectionSummaryResponse getConnectionSummary(
            Long databaseId
    ) {
        DiagnosticTarget target =
                getTarget(databaseId);

        return ConnectionSummaryResponse.from(
                databaseId,
                target.database().getEngine(),
                target.port().getConnectionSummary(
                        target.database(),
                        target.credential()
                )
        );
    }
}