package com.dbfleetops.policy.application;

import com.dbfleetops.database.application.DatabaseConfigurationReaderPort;
import com.dbfleetops.database.application.DatabaseConfigurationReaderPortRegistry;
import com.dbfleetops.database.dto.DatabaseConfigurationItem;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.infra.ConfigurationSnapshotItemRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConfigurationSnapshotServiceTest {

    @Test
    void collectSnapshotSavesSnapshotAndItems() {
        DatabaseConfigurationReaderPort mysqlReader = mock(DatabaseConfigurationReaderPort.class);

        when(mysqlReader.supports()).thenReturn(ConfigurationEngineType.MYSQL);

        when(mysqlReader.collectConfiguration(1L)).thenReturn(List.of(
                new DatabaseConfigurationItem("slow_query_log", "ON", null, null, null, "GLOBAL"),
                new DatabaseConfigurationItem("long_query_time", "1.000000", null, null, null,
                        "GLOBAL")));

        DatabaseConfigurationReaderPortRegistry registry =
                new DatabaseConfigurationReaderPortRegistry(List.of(mysqlReader));

        ConfigurationSnapshotRepository snapshotRepository =
                mock(ConfigurationSnapshotRepository.class);

        ConfigurationSnapshotItemRepository snapshotItemRepository =
                mock(ConfigurationSnapshotItemRepository.class);

        when(snapshotRepository.save(any(ConfigurationSnapshot.class))).thenAnswer(invocation -> {
            ConfigurationSnapshot snapshot = invocation.getArgument(0);

            ReflectionTestUtils.setField(snapshot, "id", 100L);

            return snapshot;
        });

        ConfigurationSnapshotService service = new ConfigurationSnapshotService(registry,
                snapshotRepository, snapshotItemRepository);

        ConfigurationSnapshot snapshot = service.collectSnapshot(1L, ConfigurationEngineType.MYSQL);

        assertThat(snapshot.getId()).isEqualTo(100L);

        assertThat(snapshot.getDatabaseId()).isEqualTo(1L);

        assertThat(snapshot.getEngineType()).isEqualTo(ConfigurationEngineType.MYSQL);

        ArgumentCaptor<List<ConfigurationSnapshotItem>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(snapshotItemRepository).saveAll(captor.capture());

        List<ConfigurationSnapshotItem> savedItems = captor.getValue();

        assertThat(savedItems).hasSize(2);

        assertThat(savedItems).anySatisfy(item -> {
            assertThat(item.getSnapshotId()).isEqualTo(100L);
            assertThat(item.getParameterName()).isEqualTo("slow_query_log");
            assertThat(item.getActualValue()).isEqualTo("ON");
            assertThat(item.getSource()).isEqualTo("GLOBAL");
        });

        assertThat(savedItems).anySatisfy(item -> {
            assertThat(item.getSnapshotId()).isEqualTo(100L);
            assertThat(item.getParameterName()).isEqualTo("long_query_time");
            assertThat(item.getActualValue()).isEqualTo("1.000000");
            assertThat(item.getSource()).isEqualTo("GLOBAL");
        });
    }
}
