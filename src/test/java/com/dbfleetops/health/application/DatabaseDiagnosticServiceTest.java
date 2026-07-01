package com.dbfleetops.health.application;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.health.domain.ConnectionSummary;
import com.dbfleetops.health.domain.DatabaseUptimeInfo;
import com.dbfleetops.health.domain.DatabaseVersionInfo;
import com.dbfleetops.health.domain.LockWaitInfo;
import com.dbfleetops.health.domain.LongTransactionInfo;
import com.dbfleetops.health.domain.SessionInfo;
import com.dbfleetops.health.dto.ConnectionSummaryResponse;
import com.dbfleetops.health.dto.DatabaseUptimeResponse;
import com.dbfleetops.health.dto.DatabaseVersionResponse;
import com.dbfleetops.health.dto.LockWaitResponse;
import com.dbfleetops.health.dto.LongTransactionResponse;
import com.dbfleetops.health.dto.SessionResponse;
import com.dbfleetops.health.port.DatabaseDiagnosticPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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

    @Test
    void getLongTransactionsReturnsLongTransactionResponses() {
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

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 7, 1, 15, 30);

        when(databaseRepository.findById(1L))
                .thenReturn(Optional.of(database));

        when(credentialRepository.findByDatabaseId(1L))
                .thenReturn(Optional.of(credential));

        when(portRegistry.getPort(DatabaseEngine.MYSQL))
                .thenReturn(diagnosticPort);

        when(diagnosticPort.getLongTransactions(database, credential))
                .thenReturn(List.of(
                        new LongTransactionInfo(
                                "12345",
                                "RUNNING",
                                startedAt,
                                120L,
                                10L,
                                "UPDATE orders SET status = 'PAID'"
                        )
                ));

        List<LongTransactionResponse> responses =
                service.getLongTransactions(1L);

        assertThat(responses)
                .hasSize(1);

        LongTransactionResponse response =
                responses.getFirst();

        assertThat(response.databaseId())
                .isEqualTo(1L);

        assertThat(response.engine())
                .isEqualTo(DatabaseEngine.MYSQL);

        assertThat(response.transactionId())
                .isEqualTo("12345");

        assertThat(response.durationSeconds())
                .isEqualTo(120L);

        assertThat(response.threadId())
                .isEqualTo(10L);

        verify(diagnosticPort)
                .getLongTransactions(database, credential);
    }

    @Test
    void getLockWaitsReturnsLockWaitResponses() {
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

        when(diagnosticPort.getLockWaits(database, credential))
                .thenReturn(List.of(
                        new LockWaitInfo(
                                "waiting-1",
                                11L,
                                "UPDATE orders SET status = 'PAID'",
                                "blocking-1",
                                10L,
                                "SELECT * FROM orders WHERE id = 1"
                        )
                ));

        List<LockWaitResponse> responses =
                service.getLockWaits(1L);

        assertThat(responses)
                .hasSize(1);

        LockWaitResponse response =
                responses.getFirst();

        assertThat(response.databaseId())
                .isEqualTo(1L);

        assertThat(response.engine())
                .isEqualTo(DatabaseEngine.MYSQL);

        assertThat(response.waitingTransactionId())
                .isEqualTo("waiting-1");

        assertThat(response.blockingTransactionId())
                .isEqualTo("blocking-1");

        assertThat(response.waitingThreadId())
                .isEqualTo(11L);

        assertThat(response.blockingThreadId())
                .isEqualTo(10L);

        verify(diagnosticPort)
                .getLockWaits(database, credential);
    }
}