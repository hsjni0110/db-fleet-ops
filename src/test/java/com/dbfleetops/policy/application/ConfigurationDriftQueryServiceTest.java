package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ConfigurationDrift;
import com.dbfleetops.policy.domain.ConfigurationDriftItem;
import com.dbfleetops.policy.domain.ConfigurationDriftStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationDriftResponse;
import com.dbfleetops.policy.infra.ConfigurationDriftItemRepository;
import com.dbfleetops.policy.infra.ConfigurationDriftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConfigurationDriftQueryServiceTest {

    private final ConfigurationDriftRepository driftRepository =
            mock(ConfigurationDriftRepository.class);

    private final ConfigurationDriftItemRepository driftItemRepository =
            mock(ConfigurationDriftItemRepository.class);

    private final ConfigurationDriftService service =
            new ConfigurationDriftService(driftRepository, driftItemRepository);

    @Test
    void getLatestDriftReturnsLatestDriftWithItems() {
        ConfigurationDrift drift = createDrift(10L, ConfigurationDriftStatus.NON_COMPLIANT);

        ConfigurationDriftItem item = createDriftItem(100L, 10L, "long_query_time", "1.0",
                "10.000000", ComplianceStatus.NON_COMPLIANT);

        when(driftRepository.findFirstByDatabaseIdOrderByCheckedAtDesc(1L))
                .thenReturn(Optional.of(drift));

        when(driftRepository.findById(10L)).thenReturn(Optional.of(drift));

        when(driftItemRepository.findByDriftIdOrderByParameterNameAsc(10L))
                .thenReturn(List.of(item));

        ConfigurationDriftResponse response = service.getLatestDrift(1L);

        assertThat(response.driftId()).isEqualTo(10L);

        assertThat(response.databaseId()).isEqualTo(1L);

        assertThat(response.status()).isEqualTo(ConfigurationDriftStatus.NON_COMPLIANT);

        assertThat(response.items()).hasSize(1);

        assertThat(response.items().get(0).parameterName()).isEqualTo("long_query_time");
    }

    @Test
    void getDriftsByDatabaseIdReturnsSummaryListWithoutItems() {
        ConfigurationDrift latest = createDrift(10L, ConfigurationDriftStatus.NON_COMPLIANT);

        ConfigurationDrift previous = createDrift(9L, ConfigurationDriftStatus.COMPLIANT);

        when(driftRepository.findTop10ByDatabaseIdOrderByCheckedAtDesc(1L))
                .thenReturn(List.of(latest, previous));

        List<ConfigurationDriftResponse> responses = service.getDriftsByDatabaseId(1L);

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).driftId()).isEqualTo(10L);

        assertThat(responses.get(0).items()).isEmpty();

        assertThat(responses.get(1).driftId()).isEqualTo(9L);

        assertThat(responses.get(1).items()).isEmpty();
    }

    @Test
    void getDriftReturnsDriftDetailWithItems() {
        ConfigurationDrift drift = createDrift(10L, ConfigurationDriftStatus.NON_COMPLIANT);

        ConfigurationDriftItem item = createDriftItem(100L, 10L, "slow_query_log", "ON", "OFF",
                ComplianceStatus.NON_COMPLIANT);

        when(driftRepository.findById(10L)).thenReturn(Optional.of(drift));

        when(driftItemRepository.findByDriftIdOrderByParameterNameAsc(10L))
                .thenReturn(List.of(item));

        ConfigurationDriftResponse response = service.getDrift(10L);

        assertThat(response.driftId()).isEqualTo(10L);

        assertThat(response.items()).hasSize(1);

        assertThat(response.items().get(0).parameterName()).isEqualTo("slow_query_log");
    }

    private ConfigurationDrift createDrift(Long driftId, ConfigurationDriftStatus status) {
        ConfigurationDrift drift =
                ConfigurationDrift.create(1L, 3L, 7L, ConfigurationEngineType.MYSQL, status, 4,
                        status == ConfigurationDriftStatus.COMPLIANT ? 4 : 2,
                        status == ConfigurationDriftStatus.COMPLIANT ? 0 : 1,
                        status == ConfigurationDriftStatus.COMPLIANT ? 0 : 1);

        ReflectionTestUtils.setField(drift, "id", driftId);

        return drift;
    }

    private ConfigurationDriftItem createDriftItem(Long itemId, Long driftId, String parameterName,
            String expectedValue, String actualValue, ComplianceStatus complianceStatus) {
        ConfigurationDriftItem item = ConfigurationDriftItem.create(driftId, parameterName,
                expectedValue, actualValue, ParameterValueType.STRING, true, true, true,
                complianceStatus, "test message");

        ReflectionTestUtils.setField(item, "id", itemId);

        return item;
    }
}
