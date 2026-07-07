package com.dbfleetops.operation.application;

import com.dbfleetops.database.application.DatabaseConfigurationApplyPort;
import com.dbfleetops.database.application.DatabaseConfigurationApplyPortRegistry;
import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.policy.application.ConfigurationApplyValidationService;
import com.dbfleetops.policy.application.ConfigurationSnapshotService;
import com.dbfleetops.policy.application.ConfigurationValueComparator;
import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
import com.dbfleetops.policy.infra.ConfigurationApplyItemRepository;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConfigurationApplyJobExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ManagedDatabaseRepository databaseRepository =
            mock(ManagedDatabaseRepository.class);

    private final ConfigurationApplyValidationService validationService =
            mock(ConfigurationApplyValidationService.class);

    private final ConfigurationSnapshotService snapshotService =
            mock(ConfigurationSnapshotService.class);

    private final ConfigurationSnapshotItemRepository snapshotItemRepository =
            mock(ConfigurationSnapshotItemRepository.class);

    private final ConfigurationApplyRepository applyRepository =
            mock(ConfigurationApplyRepository.class);

    private final ConfigurationApplyItemRepository applyItemRepository =
            mock(ConfigurationApplyItemRepository.class);

    private final DatabaseConfigurationApplyPortRegistry applyPortRegistry =
            mock(DatabaseConfigurationApplyPortRegistry.class);

    private final ConfigurationValueComparator valueComparator = new ConfigurationValueComparator();

    private final ConfigurationApplyJobExecutor executor =
            new ConfigurationApplyJobExecutor(objectMapper, databaseRepository, validationService,
                    snapshotService, snapshotItemRepository, applyRepository, applyItemRepository,
                    applyPortRegistry, valueComparator);

    @Test
    void executeConfigurationApplyJobCreatesApplyAndVerifiesItems() {
        OperationJob job =
                OperationJob.create(JobType.CONFIGURATION_APPLY, 1L, "local-user", "idem-apply-001",
                        """
                                {"profileId":1,"reason":"enable slow query log","requestedBy":"local-user","parameters":[{"parameterName":"slow_query_log","targetValue":"ON"}]}
                                """
                                .trim());

        ReflectionTestUtils.setField(job, "id", 100L);

        ManagedDatabase database = mock(ManagedDatabase.class);

        when(database.getId()).thenReturn(1L);

        when(database.getEngine()).thenReturn(DatabaseEngine.MYSQL);

        when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

        when(validationService.validate(eq(1L), any()))
                .thenReturn(new ConfigurationApplyValidationResult(1L, 1L,
                        List.of(new ConfigurationApplyValidationItem("slow_query_log", "ON",
                                ParameterValueType.BOOLEAN, true, true))));

        when(applyRepository.save(any(ConfigurationApply.class))).thenAnswer(invocation -> {
            ConfigurationApply apply = invocation.getArgument(0);

            ReflectionTestUtils.setField(apply, "id", 10L);

            return apply;
        });

        when(applyItemRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ConfigurationApplyItem> items = new ArrayList<>(invocation.getArgument(0));

            for (int index = 0; index < items.size(); index++) {
                ReflectionTestUtils.setField(items.get(index), "id", (long) index + 1);
            }

            return items;
        });

        ConfigurationSnapshot beforeSnapshot =
                ConfigurationSnapshot.create(1L, ConfigurationEngineType.MYSQL);

        ReflectionTestUtils.setField(beforeSnapshot, "id", 20L);

        ConfigurationSnapshot afterSnapshot =
                ConfigurationSnapshot.create(1L, ConfigurationEngineType.MYSQL);

        ReflectionTestUtils.setField(afterSnapshot, "id", 21L);

        when(snapshotService.collectSnapshot(1L, ConfigurationEngineType.MYSQL))
                .thenReturn(beforeSnapshot, afterSnapshot);

        when(snapshotItemRepository.findBySnapshotIdOrderByParameterNameAsc(20L))
                .thenReturn(List.of(ConfigurationSnapshotItem.create(20L, "slow_query_log", "OFF",
                        null, "BOOLEAN", true, "GLOBAL")));

        when(snapshotItemRepository.findBySnapshotIdOrderByParameterNameAsc(21L))
                .thenReturn(List.of(ConfigurationSnapshotItem.create(21L, "slow_query_log", "ON",
                        null, "BOOLEAN", true, "GLOBAL")));

        DatabaseConfigurationApplyPort applyPort = mock(DatabaseConfigurationApplyPort.class);

        when(applyPortRegistry.getApplyPort(ConfigurationEngineType.MYSQL)).thenReturn(applyPort);

        when(applyPort.applyGlobalParameter(1L, "slow_query_log", "ON", ParameterValueType.BOOLEAN))
                .thenReturn(
                        ConfigurationApplyCommandResult.success("slow_query_log", "ON", "applied"));

        ConfigurationApply result = executor.execute(job);

        assertThat(result.getId()).isEqualTo(10L);

        assertThat(result.getBeforeSnapshotId()).isEqualTo(20L);

        assertThat(result.getAfterSnapshotId()).isEqualTo(21L);

        assertThat(result.getSuccessCount()).isEqualTo(1);

        assertThat(result.getFailedCount()).isZero();

        verify(validationService).validate(eq(1L), any());

        verify(snapshotService, times(2)).collectSnapshot(1L, ConfigurationEngineType.MYSQL);

        verify(applyPortRegistry).getApplyPort(ConfigurationEngineType.MYSQL);

        verify(applyPort).applyGlobalParameter(1L, "slow_query_log", "ON",
                ParameterValueType.BOOLEAN);
    }
}
