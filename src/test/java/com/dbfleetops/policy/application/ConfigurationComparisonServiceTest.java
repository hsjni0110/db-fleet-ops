package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationComparisonResult;
import com.dbfleetops.policy.infra.ConfigurationProfileParameterRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotItemRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigurationComparisonServiceTest {

    private final ConfigurationProfileRepository profileRepository =
            mock(ConfigurationProfileRepository.class);

    private final ConfigurationProfileParameterRepository parameterRepository =
            mock(ConfigurationProfileParameterRepository.class);

    private final ConfigurationSnapshotRepository snapshotRepository =
            mock(ConfigurationSnapshotRepository.class);

    private final ConfigurationSnapshotItemRepository snapshotItemRepository =
            mock(ConfigurationSnapshotItemRepository.class);

    private final ConfigurationComparisonService service =
            new ConfigurationComparisonService(profileRepository, parameterRepository,
                    snapshotRepository, snapshotItemRepository, new ConfigurationValueComparator());

    @Test
    void compareProfileWithSnapshotReturnsSummary() {
        ConfigurationProfile profile = createProfile(1L, ConfigurationEngineType.MYSQL);

        ConfigurationSnapshot snapshot = createSnapshot(10L, 100L, ConfigurationEngineType.MYSQL);

        ConfigurationProfileParameter slowQueryLog = createProfileParameter(1L, "slow_query_log",
                "ON", ParameterValueType.BOOLEAN, true, true, true);

        ConfigurationProfileParameter longQueryTime = createProfileParameter(2L, "long_query_time",
                "1.0", ParameterValueType.NUMBER, true, true, true);

        ConfigurationProfileParameter binlogFormat = createProfileParameter(3L, "binlog_format",
                "ROW", ParameterValueType.STRING, true, false, false);

        ConfigurationSnapshotItem actualSlowQueryLog =
                createSnapshotItem(101L, 10L, "slow_query_log", "1");

        ConfigurationSnapshotItem actualLongQueryTime =
                createSnapshotItem(102L, 10L, "long_query_time", "10.000000");

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapshot));

        when(parameterRepository.findByProfileIdOrderByParameterNameAsc(1L))
                .thenReturn(List.of(slowQueryLog, longQueryTime, binlogFormat));

        when(snapshotItemRepository.findBySnapshotIdOrderByParameterNameAsc(10L))
                .thenReturn(List.of(actualSlowQueryLog, actualLongQueryTime));

        ConfigurationComparisonResult result = service.compare(1L, 10L);

        assertThat(result.profileId()).isEqualTo(1L);

        assertThat(result.snapshotId()).isEqualTo(10L);

        assertThat(result.databaseId()).isEqualTo(100L);

        assertThat(result.engineType()).isEqualTo(ConfigurationEngineType.MYSQL);

        assertThat(result.totalCount()).isEqualTo(3);

        assertThat(result.compliantCount()).isEqualTo(1);

        assertThat(result.nonCompliantCount()).isEqualTo(1);

        assertThat(result.missingCount()).isEqualTo(1);

        assertThat(result.overallStatus()).isEqualTo(ComplianceStatus.NON_COMPLIANT);

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.parameterName()).isEqualTo("slow_query_log");
            assertThat(item.complianceStatus()).isEqualTo(ComplianceStatus.COMPLIANT);
            assertThat(item.actualValue()).isEqualTo("1");
        });

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.parameterName()).isEqualTo("long_query_time");
            assertThat(item.complianceStatus()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
            assertThat(item.expectedValue()).isEqualTo("1.0");
            assertThat(item.actualValue()).isEqualTo("10.000000");
        });

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.parameterName()).isEqualTo("binlog_format");
            assertThat(item.complianceStatus()).isEqualTo(ComplianceStatus.MISSING);
            assertThat(item.actualValue()).isNull();
        });
    }

    @Test
    void compareThrowsExceptionWhenEngineTypeDoesNotMatch() {
        ConfigurationProfile profile = createProfile(1L, ConfigurationEngineType.MYSQL);

        ConfigurationSnapshot snapshot =
                createSnapshot(10L, 100L, ConfigurationEngineType.POSTGRESQL);

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> service.compare(1L, 10L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("do not match");
    }

    @Test
    void compareReturnsCompliantOverallStatusWhenAllItemsMatch() {
        ConfigurationProfile profile = createProfile(1L, ConfigurationEngineType.MYSQL);

        ConfigurationSnapshot snapshot = createSnapshot(10L, 100L, ConfigurationEngineType.MYSQL);

        ConfigurationProfileParameter slowQueryLog = createProfileParameter(1L, "slow_query_log",
                "ON", ParameterValueType.BOOLEAN, true, true, true);

        ConfigurationSnapshotItem actualSlowQueryLog =
                createSnapshotItem(101L, 10L, "slow_query_log", "ON");

        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapshot));

        when(parameterRepository.findByProfileIdOrderByParameterNameAsc(1L))
                .thenReturn(List.of(slowQueryLog));

        when(snapshotItemRepository.findBySnapshotIdOrderByParameterNameAsc(10L))
                .thenReturn(List.of(actualSlowQueryLog));

        ConfigurationComparisonResult result = service.compare(1L, 10L);

        assertThat(result.overallStatus()).isEqualTo(ComplianceStatus.COMPLIANT);

        assertThat(result.compliantCount()).isEqualTo(1);

        assertThat(result.nonCompliantCount()).isZero();

        assertThat(result.missingCount()).isZero();
    }

    private ConfigurationProfile createProfile(Long profileId, ConfigurationEngineType engineType) {
        ConfigurationProfile profile = ConfigurationProfile.create("mysql-production-standard",
                engineType, "PRODUCTION", ">=8.0", "MySQL production baseline profile");

        ReflectionTestUtils.setField(profile, "id", profileId);

        return profile;
    }

    private ConfigurationProfileParameter createProfileParameter(Long parameterId,
            String parameterName, String expectedValue, ParameterValueType valueType,
            Boolean required, Boolean dynamic, Boolean applyAllowed) {
        ConfigurationProfileParameter parameter =
                ConfigurationProfileParameter.create(1L, parameterName, expectedValue, valueType,
                        required, dynamic, applyAllowed, "test parameter");

        ReflectionTestUtils.setField(parameter, "id", parameterId);

        return parameter;
    }

    private ConfigurationSnapshot createSnapshot(Long snapshotId, Long databaseId,
            ConfigurationEngineType engineType) {
        ConfigurationSnapshot snapshot = ConfigurationSnapshot.create(databaseId, engineType);

        ReflectionTestUtils.setField(snapshot, "id", snapshotId);

        return snapshot;
    }

    private ConfigurationSnapshotItem createSnapshotItem(Long itemId, Long snapshotId,
            String parameterName, String actualValue) {
        ConfigurationSnapshotItem item = ConfigurationSnapshotItem.create(snapshotId, parameterName,
                actualValue, null, null, null, "GLOBAL");

        ReflectionTestUtils.setField(item, "id", itemId);

        return item;
    }
}
