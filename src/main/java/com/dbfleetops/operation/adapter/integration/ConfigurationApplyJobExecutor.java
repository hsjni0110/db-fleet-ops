package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.operation.application.required.DatabaseExecutionTarget;
import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.operation.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.application.required.DatabaseReader;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ConfigurationApplyJobPayload;
import com.dbfleetops.operation.dto.ConfigurationApplyParameterRequest;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationApplyItemStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ConfigurationApplyJobExecutor {

    private final ObjectMapper objectMapper;
    private final DatabaseReader databaseRepository;
    private final ConfigurationValidator validationService;
    private final SnapshotCollector snapshotService;
    private final ConfigurationApplyStore applyStore;
    private final ConfigurationCommand configurationCommand;
    private final ConfigurationValueMatcher valueComparator;

    public ConfigurationApplyJobExecutor(ObjectMapper objectMapper,
            DatabaseReader databaseRepository,
            ConfigurationValidator validationService,
            SnapshotCollector snapshotService,
            ConfigurationApplyStore applyStore,
            ConfigurationCommand configurationCommand,
            ConfigurationValueMatcher valueComparator) {
        this.objectMapper = objectMapper;
        this.databaseRepository = databaseRepository;
        this.validationService = validationService;
        this.snapshotService = snapshotService;
        this.applyStore = applyStore;
        this.configurationCommand = configurationCommand;
        this.valueComparator = valueComparator;
    }

    @Transactional
    public ConfigurationApply execute(OperationJob job) {
        if (job == null) {
            throw new IllegalArgumentException("job is required.");
        }

        ConfigurationApplyJobPayload payload = parsePayload(job.getRequestPayload());

        validatePayload(payload);

        DatabaseExecutionTarget database = databaseRepository.findDatabase(job.getTargetDatabaseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Database not found. databaseId=" + job.getTargetDatabaseId()));

        ConfigurationEngineType engineType = toConfigurationEngineType(database);

        ConfigurationApplyValidationResult validationResult =
                validationService.validate(database.id(), toValidationRequest(payload));

        ConfigurationApply apply = ConfigurationApply.create(database.id(), job.getId(),
                payload.requestedBy(), payload.reason(), validationResult.totalCount());

        ConfigurationApply savedApply = applyStore.saveApply(apply);

        List<ConfigurationApplyItem> applyItems =
                createApplyItems(savedApply.getId(), validationResult.items());

        List<ConfigurationApplyItem> savedApplyItems = applyStore.saveItems(applyItems);

        ConfigurationSnapshot beforeSnapshot =
                snapshotService.collect(database.id(), engineType);

        savedApply.start(beforeSnapshot.getId());

        Map<String, ConfigurationSnapshotItem> beforeSnapshotItemMap =
                loadSnapshotItemMap(beforeSnapshot.getId());

        savedApplyItems.forEach(item -> item
                .markBeforeValue(findActualValue(beforeSnapshotItemMap, item.getParameterName())));

        for (ConfigurationApplyItem item : savedApplyItems) {
            applySingleItem(engineType, database.id(), item);
        }

        ConfigurationSnapshot afterSnapshot =
                snapshotService.collect(database.id(), engineType);

        Map<String, ConfigurationSnapshotItem> afterSnapshotItemMap =
                loadSnapshotItemMap(afterSnapshot.getId());

        verifyAppliedItems(savedApplyItems, afterSnapshotItemMap);

        int successCount = countByStatus(savedApplyItems, ConfigurationApplyItemStatus.VERIFIED);

        int failedCount = countFailedItems(savedApplyItems);

        int skippedCount = countByStatus(savedApplyItems, ConfigurationApplyItemStatus.SKIPPED)
                + countByStatus(savedApplyItems, ConfigurationApplyItemStatus.UNSUPPORTED);

        savedApply.complete(afterSnapshot.getId(), successCount, failedCount, skippedCount);

        return savedApply;
    }

    private ConfigurationApplyJobPayload parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("Configuration apply job payload is required.");
        }

        try {
            return objectMapper.readValue(payloadJson, ConfigurationApplyJobPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid configuration apply job payload.",
                    exception);
        }
    }

    private void validatePayload(ConfigurationApplyJobPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required.");
        }

        if (payload.profileId() == null) {
            throw new IllegalArgumentException("profileId is required.");
        }

        if (payload.requestedBy() == null || payload.requestedBy().isBlank()) {
            throw new IllegalArgumentException("requestedBy is required.");
        }

        if (payload.parameters() == null || payload.parameters().isEmpty()) {
            throw new IllegalArgumentException("parameters is required.");
        }
    }

    private CreateConfigurationApplyJobRequest toValidationRequest(
            ConfigurationApplyJobPayload payload) {
        return new CreateConfigurationApplyJobRequest(payload.profileId(), payload.requestedBy(),
                payload.reason(),
                payload.parameters().stream()
                        .map(parameter -> new ConfigurationApplyParameterRequest(
                                parameter.parameterName(), parameter.targetValue()))
                        .toList());
    }

    private List<ConfigurationApplyItem> createApplyItems(Long applyId,
            List<ConfigurationApplyValidationItem> validationItems) {
        return validationItems.stream()
                .sorted(Comparator.comparing(ConfigurationApplyValidationItem::parameterName))
                .map(item -> ConfigurationApplyItem.create(applyId, item.parameterName(),
                        item.targetValue(), item.valueType(), item.dynamic(), item.applyAllowed()))
                .toList();
    }

    private void applySingleItem(ConfigurationEngineType engineType, Long databaseId,
            ConfigurationApplyItem item) {
        try {
            ConfigurationApplyCommandResult commandResult =
                    configurationCommand.apply(engineType, databaseId, item.getParameterName(),
                            item.getRequestedValue(), item.getValueType());

            if (commandResult.success()) {
                item.markApplied();
                return;
            }

            item.markFailed("APPLY_COMMAND_FAILED", commandResult.message());
        } catch (Exception exception) {
            item.markFailed(exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private void verifyAppliedItems(List<ConfigurationApplyItem> items,
            Map<String, ConfigurationSnapshotItem> afterSnapshotItemMap) {
        for (ConfigurationApplyItem item : items) {
            if (item.getApplyStatus() != ConfigurationApplyItemStatus.APPLIED) {
                continue;
            }

            String afterValue = findActualValue(afterSnapshotItemMap, item.getParameterName());

            if (afterValue == null) {
                item.markFailed("VERIFY_MISSING_PARAMETER",
                        "Applied parameter is missing from after snapshot.");
                continue;
            }

            boolean matched = valueComparator.matches(item.getRequestedValue(), afterValue,
                    item.getValueType());

            if (matched) {
                item.markVerified(afterValue);
                continue;
            }

            item.markFailed("VERIFY_VALUE_MISMATCH",
                    "Requested value does not match after value. requestedValue="
                            + item.getRequestedValue() + ", afterValue=" + afterValue);
        }
    }

    private Map<String, ConfigurationSnapshotItem> loadSnapshotItemMap(Long snapshotId) {
        return applyStore.findSnapshotItems(snapshotId).stream()
                .collect(Collectors.toMap(item -> normalizeParameterName(item.getParameterName()),
                        Function.identity(), (first, second) -> first));
    }

    private String findActualValue(Map<String, ConfigurationSnapshotItem> snapshotItemMap,
            String parameterName) {
        ConfigurationSnapshotItem item = snapshotItemMap.get(normalizeParameterName(parameterName));

        if (item == null) {
            return null;
        }

        return item.getActualValue();
    }

    private int countByStatus(List<ConfigurationApplyItem> items,
            ConfigurationApplyItemStatus status) {
        return (int) items.stream().filter(item -> item.getApplyStatus() == status).count();
    }

    private int countFailedItems(List<ConfigurationApplyItem> items) {
        return (int) items.stream()
                .filter(item -> item.getApplyStatus() == ConfigurationApplyItemStatus.FAILED
                        || item.getApplyStatus() == ConfigurationApplyItemStatus.UNSUPPORTED)
                .count();
    }

    private ConfigurationEngineType toConfigurationEngineType(DatabaseExecutionTarget database) {
        if (database.engine() == null) {
            throw new IllegalArgumentException(
                        "Database engineType is required. databaseId=" + database.id());
        }

        return ConfigurationEngineType.valueOf(database.engine());
    }

    private String normalizeParameterName(String parameterName) {
        return parameterName.trim().toLowerCase(Locale.ROOT);
    }
}
