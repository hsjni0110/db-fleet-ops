package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.health.domain.DatabaseUptimeInfo;
import com.dbfleetops.health.domain.DatabaseVersionInfo;
import com.dbfleetops.health.dto.DatabaseUptimeResponse;
import com.dbfleetops.health.dto.DatabaseVersionResponse;
import com.dbfleetops.health.port.DatabaseDiagnosticPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseDiagnosticServiceTest {

    @Mock
    private ManagedDatabaseRepository databaseRepository;

    @Mock
    private DatabaseCredentialRepository credentialRepository;

    @Mock
    private DatabaseDiagnosticPortRegistry portRegistry;

    @Mock
    private DatabaseDiagnosticPort diagnosticPort;

    @Test
    void getVersionReturnsDatabaseVersion() {
        ManagedDatabase database = newDatabase();

        DatabaseCredential credential =
                new DatabaseCredential(
                        1L,
                        "root",
                        "password"
                );

        DatabaseDiagnosticService service =
                new DatabaseDiagnosticService(
                        databaseRepository,
                        credentialRepository,
                        portRegistry
                );

        when(databaseRepository.findById(1L))
                .thenReturn(Optional.of(database));

        when(credentialRepository.findByDatabaseId(1L))
                .thenReturn(Optional.of(credential));

        when(portRegistry.getPort(DatabaseEngine.MYSQL))
                .thenReturn(diagnosticPort);

        when(diagnosticPort.getVersion(database, credential))
                .thenReturn(new DatabaseVersionInfo("8.4.0"));

        DatabaseVersionResponse response =
                service.getVersion(1L);

        assertThat(response.databaseId())
                .isEqualTo(1L);

        assertThat(response.engine())
                .isEqualTo(DatabaseEngine.MYSQL);

        assertThat(response.version())
                .isEqualTo("8.4.0");

        verify(diagnosticPort)
                .getVersion(database, credential);
    }

    @Test
    void getUptimeReturnsDatabaseUptime() {
        ManagedDatabase database = newDatabase();

        DatabaseCredential credential =
                new DatabaseCredential(
                        1L,
                        "root",
                        "password"
                );

        DatabaseDiagnosticService service =
                new DatabaseDiagnosticService(
                        databaseRepository,
                        credentialRepository,
                        portRegistry
                );

        when(databaseRepository.findById(1L))
                .thenReturn(Optional.of(database));

        when(credentialRepository.findByDatabaseId(1L))
                .thenReturn(Optional.of(credential));

        when(portRegistry.getPort(DatabaseEngine.MYSQL))
                .thenReturn(diagnosticPort);

        when(diagnosticPort.getUptime(database, credential))
                .thenReturn(new DatabaseUptimeInfo(3600L));

        DatabaseUptimeResponse response =
                service.getUptime(1L);

        assertThat(response.databaseId())
                .isEqualTo(1L);

        assertThat(response.engine())
                .isEqualTo(DatabaseEngine.MYSQL);

        assertThat(response.uptimeSeconds())
                .isEqualTo(3600L);

        verify(diagnosticPort)
                .getUptime(database, credential);
    }

    @Test
    void getVersionDoesNotCallPortWhenDatabaseIsInactive() {
        ManagedDatabase database = newDatabase();

        database.deactivate();

        DatabaseDiagnosticService service =
                new DatabaseDiagnosticService(
                        databaseRepository,
                        credentialRepository,
                        portRegistry
                );

        when(databaseRepository.findById(1L))
                .thenReturn(Optional.of(database));

        assertThrows(
                IllegalStateException.class,
                () -> service.getVersion(1L)
        );

        verifyNoInteractions(credentialRepository);
        verifyNoInteractions(portRegistry);
        verifyNoInteractions(diagnosticPort);
    }

    private ManagedDatabase newDatabase() {
        return new ManagedDatabase(
                "order-mysql",
                "localhost",
                3306,
                "orders",
                DatabaseEngine.MYSQL,
                "LOCAL",
                "order-service",
                "platform-team",
                "test database"
        );
    }
}