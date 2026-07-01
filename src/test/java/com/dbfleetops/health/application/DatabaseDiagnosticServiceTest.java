package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.health.domain.ConnectionSummary;
import com.dbfleetops.health.domain.DatabaseUptimeInfo;
import com.dbfleetops.health.domain.DatabaseVersionInfo;
import com.dbfleetops.health.domain.SessionInfo;
import com.dbfleetops.health.dto.ConnectionSummaryResponse;
import com.dbfleetops.health.dto.DatabaseUptimeResponse;
import com.dbfleetops.health.dto.DatabaseVersionResponse;
import com.dbfleetops.health.dto.SessionResponse;
import com.dbfleetops.health.port.DatabaseDiagnosticPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    @Test
    void getConnectionSummaryReturnsConnectionSummary() {
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

        when(diagnosticPort.getConnectionSummary(database, credential))
                .thenReturn(new ConnectionSummary(
                        12,
                        2,
                        151,
                        7.95
                ));

        ConnectionSummaryResponse response =
                service.getConnectionSummary(1L);

        assertThat(response.databaseId())
                .isEqualTo(1L);

        assertThat(response.engine())
                .isEqualTo(DatabaseEngine.MYSQL);

        assertThat(response.currentConnections())
                .isEqualTo(12);

        assertThat(response.runningConnections())
                .isEqualTo(2);

        assertThat(response.maxConnections())
                .isEqualTo(151);

        assertThat(response.usagePercent())
                .isEqualTo(7.95);

        verify(diagnosticPort)
                .getConnectionSummary(database, credential);
    }

    @Test
    void getSessionsReturnsSessionResponses() {
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

        when(diagnosticPort.getSessions(database, credential))
                .thenReturn(List.of(
                        new SessionInfo(
                                10L,
                                "db_monitor",
                                "localhost:50000",
                                "orders",
                                "Query",
                                3L,
                                "executing",
                                "SELECT * FROM orders"
                        )
                ));

        List<SessionResponse> responses =
                service.getSessions(1L);

        assertThat(responses)
                .hasSize(1);

        SessionResponse response =
                responses.getFirst();

        assertThat(response.databaseId())
                .isEqualTo(1L);

        assertThat(response.engine())
                .isEqualTo(DatabaseEngine.MYSQL);

        assertThat(response.processId())
                .isEqualTo(10L);

        assertThat(response.user())
                .isEqualTo("db_monitor");

        assertThat(response.command())
                .isEqualTo("Query");

        assertThat(response.queryPreview())
                .isEqualTo("SELECT * FROM orders");

        verify(diagnosticPort)
                .getSessions(database, credential);
    }
}