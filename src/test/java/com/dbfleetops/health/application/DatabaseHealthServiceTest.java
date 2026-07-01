package com.dbfleetops.health.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.DatabaseCredentialRepository;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.health.domain.DatabaseErrorCode;
import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.domain.DatabaseHealthResult;
import com.dbfleetops.health.domain.DatabaseStatus;
import com.dbfleetops.health.domain.HealthStatus;
import com.dbfleetops.health.dto.DatabaseHealthResponse;
import com.dbfleetops.health.infra.DatabaseHealthResultRepository;
import com.dbfleetops.health.port.DatabaseHealthProbe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class DatabaseHealthServiceTest {

    @Mock
    private DatabaseHealthProbe databaseHealthProbe;

    @Mock
    private ManagedDatabaseRepository databaseRepository;

    @Mock
    private DatabaseCredentialRepository credentialRepository;

    @Mock
    private DatabaseHealthAdapterFactory adapterFactory;

    @Mock
    private DatabaseHealthResultRepository healthResultRepository;

    private DatabaseHealthService service;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        service = new DatabaseHealthService(
                databaseHealthProbe,
                databaseRepository,
                credentialRepository,
                adapterFactory,
                healthResultRepository
        );

        logger = (Logger) LoggerFactory.getLogger(
                DatabaseHealthService.class
        );

        appender = new ListAppender<>();
        appender.start();

        logger.addAppender(appender);
    }

    @AfterEach
    void tearDownLogger() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void returnsUpHealthResultAndLogsAtInfoLevel() {
        DatabaseHealth expected = DatabaseHealth.up(
                "MYSQL",
                "127.0.0.1",
                3306,
                12L,
                OffsetDateTime.parse(
                        "2026-06-29T17:30:00+09:00"
                )
        );

        when(databaseHealthProbe.check())
                .thenReturn(expected);

        DatabaseHealth actual =
                service.checkDefaultDatabase();

        assertThat(actual)
                .isEqualTo(expected);

        assertThat(actual.status())
                .isEqualTo(DatabaseStatus.UP);

        verify(databaseHealthProbe).check();

        assertThat(appender.list)
                .hasSize(1);

        ILoggingEvent event =
                appender.list.getFirst();

        assertThat(event.getLevel())
                .isEqualTo(Level.INFO);

        assertThat(event.getFormattedMessage())
                .contains(
                        "database_health_checked",
                        "databaseType=MYSQL",
                        "host=127.0.0.1",
                        "port=3306",
                        "status=UP",
                        "latencyMs=12",
                        "errorCode=NONE"
                );
    }

    @Test
    void returnsDownHealthResultAndLogsAtWarnLevel() {
        DatabaseHealth expected = DatabaseHealth.down(
                "MYSQL",
                "127.0.0.1",
                3306,
                1000L,
                OffsetDateTime.parse(
                        "2026-06-29T17:31:00+09:00"
                ),
                DatabaseErrorCode.CONNECTION_REFUSED,
                "Database connection was refused."
        );

        when(databaseHealthProbe.check())
                .thenReturn(expected);

        DatabaseHealth actual =
                service.checkDefaultDatabase();

        assertThat(actual)
                .isEqualTo(expected);

        assertThat(actual.status())
                .isEqualTo(DatabaseStatus.DOWN);

        assertThat(actual.errorCode())
                .isEqualTo(
                        DatabaseErrorCode.CONNECTION_REFUSED
                );

        verify(databaseHealthProbe).check();

        assertThat(appender.list)
                .hasSize(1);

        ILoggingEvent event =
                appender.list.getFirst();

        assertThat(event.getLevel())
                .isEqualTo(Level.WARN);

        assertThat(event.getFormattedMessage())
                .contains(
                        "database_health_checked",
                        "databaseType=MYSQL",
                        "host=127.0.0.1",
                        "port=3306",
                        "status=DOWN",
                        "latencyMs=1000",
                        "errorCode=CONNECTION_REFUSED"
                );
    }

    @Test
    void logsAuthenticationFailureErrorCode() {
        DatabaseHealth expected = DatabaseHealth.down(
                "MYSQL",
                "127.0.0.1",
                3306,
                45L,
                OffsetDateTime.parse(
                        "2026-06-29T17:32:00+09:00"
                ),
                DatabaseErrorCode.AUTHENTICATION_FAILED,
                "Database authentication failed."
        );

        when(databaseHealthProbe.check())
                .thenReturn(expected);

        DatabaseHealth actual =
                service.checkDefaultDatabase();

        assertThat(actual.status())
                .isEqualTo(DatabaseStatus.DOWN);

        assertThat(actual.errorCode())
                .isEqualTo(
                        DatabaseErrorCode.AUTHENTICATION_FAILED
                );

        assertThat(appender.list)
                .hasSize(1);

        ILoggingEvent event =
                appender.list.getFirst();

        assertThat(event.getLevel())
                .isEqualTo(Level.WARN);

        assertThat(event.getFormattedMessage())
                .contains(
                        "status=DOWN",
                        "errorCode=AUTHENTICATION_FAILED"
                )
                .doesNotContain(
                        "password",
                        "local_monitor_password"
                );
    }

    @Test
    void checkByDatabaseIdSelectsAdapterAndSavesResult() {
        ManagedDatabase database = new ManagedDatabase(
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

        DatabaseCredential credential = new DatabaseCredential(
                1L,
                "root",
                "password"
        );

        DatabaseHealthAdapter adapter =
                mock(DatabaseHealthAdapter.class);

        DatabaseHealthAdapter.HealthCheckResult checkResult =
                new DatabaseHealthAdapter.HealthCheckResult(
                        HealthStatus.HEALTHY,
                        true,
                        15L,
                        "MySQL connection check succeeded."
                );

        when(databaseRepository.findById(1L))
                .thenReturn(Optional.of(database));

        when(credentialRepository.findByDatabaseId(1L))
                .thenReturn(Optional.of(credential));

        when(adapterFactory.getAdapter(DatabaseEngine.MYSQL))
                .thenReturn(adapter);

        when(adapter.check(database, credential))
                .thenReturn(checkResult);

        when(healthResultRepository.save(any(DatabaseHealthResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DatabaseHealthResponse response =
                service.check(1L);

        assertThat(response.databaseId())
                .isEqualTo(1L);

        assertThat(response.status())
                .isEqualTo(HealthStatus.HEALTHY);

        assertThat(response.connectionSuccess())
                .isTrue();

        assertThat(response.responseTimeMs())
                .isEqualTo(15L);

        verify(adapterFactory)
                .getAdapter(DatabaseEngine.MYSQL);

        verify(adapter)
                .check(database, credential);

        verify(healthResultRepository)
                .save(any(DatabaseHealthResult.class));
    }

    @Test
    void checkByDatabaseIdDoesNotRunAdapterWhenDatabaseIsInactive() {
        ManagedDatabase database = new ManagedDatabase(
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

        database.deactivate();

        when(databaseRepository.findById(1L))
                .thenReturn(Optional.of(database));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.check(1L)
        );

        verifyNoInteractions(adapterFactory);
        verifyNoInteractions(healthResultRepository);
    }
}