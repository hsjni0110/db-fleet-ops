package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ConfigurationDrift;
import com.dbfleetops.policy.domain.ConfigurationDriftItem;
import com.dbfleetops.policy.domain.ConfigurationDriftStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationComparisonItem;
import com.dbfleetops.policy.dto.ConfigurationComparisonResult;
import com.dbfleetops.policy.dto.ConfigurationDriftResponse;
import com.dbfleetops.policy.infra.ConfigurationDriftItemRepository;
import com.dbfleetops.policy.infra.ConfigurationDriftRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConfigurationDriftServiceTest {

    private final ConfigurationDriftRepository driftRepository =
            mock(ConfigurationDriftRepository.class);

    private final ConfigurationDriftItemRepository driftItemRepository =
            mock(ConfigurationDriftItemRepository.class);

    private final ConfigurationDriftService service =
            new ConfigurationDriftService(driftRepository, driftItemRepository);

    @Test
    void saveDriftPersistsDriftAndItems() {
        ConfigurationComparisonResult comparisonResult = createComparisonResult();

        when(driftRepository.save(any(ConfigurationDrift.class))).thenAnswer(invocation -> {
            ConfigurationDrift drift = invocation.getArgument(0);

            ReflectionTestUtils.setField(drift, "id", 1000L);

            return drift;
        });

        when(driftItemRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ConfigurationDriftItem> items = invocation.getArgument(0);

            long id = 1L;

            for (ConfigurationDriftItem item : items) {
                ReflectionTestUtils.setField(item, "id", id++);
            }

            return items;
        });

        ConfigurationDriftResponse response = service.saveDrift(comparisonResult);

        assertThat(response.driftId()).isEqualTo(1000L);

        assertThat(response.databaseId()).isEqualTo(1L);

        assertThat(response.profileId()).isEqualTo(10L);

        assertThat(response.snapshotId()).isEqualTo(20L);

        assertThat(response.status()).isEqualTo(ConfigurationDriftStatus.NON_COMPLIANT);

        assertThat(response.totalCount()).isEqualTo(3);

        assertThat(response.compliantCount()).isEqualTo(1);

        assertThat(response.nonCompliantCount()).isEqualTo(1);

        assertThat(response.missingCount()).isEqualTo(1);

        assertThat(response.items()).hasSize(3);

        ArgumentCaptor<List<ConfigurationDriftItem>> captor = ArgumentCaptor.forClass(List.class);

        verify(driftItemRepository).saveAll(captor.capture());

        List<ConfigurationDriftItem> savedItems = captor.getValue();

        assertThat(savedItems).allSatisfy(item -> assertThat(item.getDriftId()).isEqualTo(1000L));

        assertThat(savedItems).anySatisfy(item -> {
            assertThat(item.getParameterName()).isEqualTo("slow_query_log");
            assertThat(item.getComplianceStatus()).isEqualTo(ComplianceStatus.COMPLIANT);
        });

        assertThat(savedItems).anySatisfy(item -> {
            assertThat(item.getParameterName()).isEqualTo("long_query_time");
            assertThat(item.getComplianceStatus()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
            assertThat(item.getExpectedValue()).isEqualTo("1.0");
            assertThat(item.getActualValue()).isEqualTo("10.000000");
        });

        assertThat(savedItems).anySatisfy(item -> {
            assertThat(item.getParameterName()).isEqualTo("binlog_format");
            assertThat(item.getComplianceStatus()).isEqualTo(ComplianceStatus.MISSING);
            assertThat(item.getActualValue()).isNull();
        });
    }

    @Test
    void getDriftReturnsDriftWithItems() {
        ConfigurationDrift drift = ConfigurationDrift.create(1L, 10L, 20L,
                ConfigurationEngineType.MYSQL, ConfigurationDriftStatus.NON_COMPLIANT, 2, 1, 1, 0);

        ReflectionTestUtils.setField(drift, "id", 1000L);

        ConfigurationDriftItem item = ConfigurationDriftItem.create(1000L, "long_query_time", "1.0",
                "10.000000", ParameterValueType.NUMBER, true, true, true,
                ComplianceStatus.NON_COMPLIANT, "Expected 1.0 but actual value is 10.000000.");

        ReflectionTestUtils.setField(item, "id", 1L);

        when(driftRepository.findById(1000L)).thenReturn(Optional.of(drift));

        when(driftItemRepository.findByDriftIdOrderByParameterNameAsc(1000L))
                .thenReturn(List.of(item));

        ConfigurationDriftResponse response = service.getDrift(1000L);

        assertThat(response.driftId()).isEqualTo(1000L);

        assertThat(response.items()).hasSize(1);

        assertThat(response.items().get(0).parameterName()).isEqualTo("long_query_time");
    }

    @Test
    void getLatestDriftReturnsLatestDriftByDatabaseId() {
        ConfigurationDrift drift = ConfigurationDrift.create(1L, 10L, 20L,
                ConfigurationEngineType.MYSQL, ConfigurationDriftStatus.COMPLIANT, 1, 1, 0, 0);

        ReflectionTestUtils.setField(drift, "id", 1000L);

        when(driftRepository.findFirstByDatabaseIdOrderByCheckedAtDesc(1L))
                .thenReturn(Optional.of(drift));

        when(driftRepository.findById(1000L)).thenReturn(Optional.of(drift));

        when(driftItemRepository.findByDriftIdOrderByParameterNameAsc(1000L)).thenReturn(List.of());

        ConfigurationDriftResponse response = service.getLatestDrift(1L);

        assertThat(response.driftId()).isEqualTo(1000L);

        assertThat(response.status()).isEqualTo(ConfigurationDriftStatus.COMPLIANT);
    }

    private ConfigurationComparisonResult createComparisonResult() {
        List<ConfigurationComparisonItem> items = List.of(
                new ConfigurationComparisonItem("slow_query_log", "ON", "1",
                        ParameterValueType.BOOLEAN, true, true, true, ComplianceStatus.COMPLIANT,
                        "Expected value matches actual value."),
                new ConfigurationComparisonItem("long_query_time", "1.0", "10.000000",
                        ParameterValueType.NUMBER, true, true, true, ComplianceStatus.NON_COMPLIANT,
                        "Expected 1.0 but actual value is 10.000000."),
                new ConfigurationComparisonItem("binlog_format", "ROW", null,
                        ParameterValueType.STRING, true, false, false, ComplianceStatus.MISSING,
                        "Expected parameter is missing from actual snapshot."));

        return ConfigurationComparisonResult.of(10L, 20L, 1L, ConfigurationEngineType.MYSQL, items);
    }
}
