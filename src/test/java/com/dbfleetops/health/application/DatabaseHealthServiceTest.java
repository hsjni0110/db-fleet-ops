package com.dbfleetops.health.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.domain.DatabaseStatus;
import com.dbfleetops.health.port.DatabaseHealthProbe;

@ExtendWith(MockitoExtension.class)
class DatabaseHealthServiceTest {

    @Mock
    private DatabaseHealthProbe databaseHealthProbe;

    @Test
    void returnsHealthResultFromProbe() {
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

        DatabaseHealthService service =
                new DatabaseHealthService(databaseHealthProbe);

        DatabaseHealth actual =
                service.checkDatabaseHealth();

        assertThat(actual)
                .isEqualTo(expected);

        assertThat(actual.status())
                .isEqualTo(DatabaseStatus.UP);

        verify(databaseHealthProbe).check();
    }
}