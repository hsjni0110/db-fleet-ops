package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.domain.ParameterValueType;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
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

    private final DatabaseReader databaseRepository =
            mock(DatabaseReader.class);

    private final ConfigurationValidator validationService =
            mock(ConfigurationValidator.class);

    private final SnapshotCollector snapshotService = mock(SnapshotCollector.class);
    private final ConfigurationApplyStore applyStore = mock(ConfigurationApplyStore.class);
    private final ConfigurationCommand configurationCommand = mock(ConfigurationCommand.class);
    private final ConfigurationValueMatcher valueComparator = mock(ConfigurationValueMatcher.class);

    private final ConfigurationApplyJobExecutor executor =
            new ConfigurationApplyJobExecutor(objectMapper, databaseRepository, validationService,
                    snapshotService, applyStore, configurationCommand, valueComparator);

    @Test
    void executeConfigurationApplyJobCreatesApplyAndVerifiesItems() {
        OperationJob job =
                OperationJob.create(JobType.CONFIGURATION_APPLY, 1L, "local-user", "idem-apply-001",
                        """
                                {"profileId":1,"reason":"enable slow query log","requestedBy":"local-user","parameters":[{"parameterName":"slow_query_log","targetValue":"ON"}]}
                                """
                                .trim());

        ReflectionTestUtils.setField(job, "id", 100L);

        when(databaseRepository.findDatabase(1L)).thenReturn(Optional.of(
                new DatabaseExecutionTarget(1L, "orders", "mysql", 3306, "MYSQL", 1L, true)));

        when(validationService.validate(eq(1L), any()))
                .thenReturn(new ConfigurationApplyValidationResult(1L, 1L,
                        List.of(new ConfigurationApplyValidationItem("slow_query_log", "ON",
                                ParameterValueType.BOOLEAN, true, true))));

        when(applyStore.saveApply(any(ConfigurationApply.class))).thenAnswer(invocation -> {
            ConfigurationApply apply = invocation.getArgument(0);

            ReflectionTestUtils.setField(apply, "id", 10L);

            return apply;
        });

        when(applyStore.saveItems(anyList())).thenAnswer(invocation -> {
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

        when(snapshotService.collect(1L, ConfigurationEngineType.MYSQL))
                .thenReturn(beforeSnapshot, afterSnapshot);

        when(applyStore.findSnapshotItems(20L))
                .thenReturn(List.of(ConfigurationSnapshotItem.create(20L, "slow_query_log", "OFF",
                        null, "BOOLEAN", true, "GLOBAL")));

        when(applyStore.findSnapshotItems(21L))
                .thenReturn(List.of(ConfigurationSnapshotItem.create(21L, "slow_query_log", "ON",
                        null, "BOOLEAN", true, "GLOBAL")));

        when(configurationCommand.apply(ConfigurationEngineType.MYSQL, 1L, "slow_query_log", "ON",
                ParameterValueType.BOOLEAN))
                .thenReturn(
                        ConfigurationApplyCommandResult.success("slow_query_log", "ON", "applied"));
        when(valueComparator.matches("ON", "ON", ParameterValueType.BOOLEAN)).thenReturn(true);

        ConfigurationApply result = executor.execute(job);

        assertThat(result.getId()).isEqualTo(10L);

        assertThat(result.getBeforeSnapshotId()).isEqualTo(20L);

        assertThat(result.getAfterSnapshotId()).isEqualTo(21L);

        assertThat(result.getSuccessCount()).isEqualTo(1);

        assertThat(result.getFailedCount()).isZero();

        verify(validationService).validate(eq(1L), any());

        verify(snapshotService, times(2)).collect(1L, ConfigurationEngineType.MYSQL);

        verify(configurationCommand).apply(ConfigurationEngineType.MYSQL, 1L, "slow_query_log", "ON",
                ParameterValueType.BOOLEAN);
    }
}
