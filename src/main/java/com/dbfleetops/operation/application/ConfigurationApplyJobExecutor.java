package com.dbfleetops.operation.application;

import com.dbfleetops.database.application.DatabaseConfigurationApplyPort;
import com.dbfleetops.database.application.DatabaseConfigurationApplyPortRegistry;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ConfigurationApplyJobPayload;
import com.dbfleetops.operation.dto.ConfigurationApplyParameterRequest;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.policy.application.ConfigurationApplyValidationService;
import com.dbfleetops.policy.application.ConfigurationSnapshotService;
import com.dbfleetops.policy.application.ConfigurationValueComparator;
import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationApplyItemStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
import com.dbfleetops.policy.infra.ConfigurationApplyItemRepository;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotItemRepository;
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
    private final ManagedDatabaseRepository databaseRepository;
    private final ConfigurationApplyValidationService validationService;
    private final ConfigurationSnapshotService snapshotService;
    private final ConfigurationSnapshotItemRepository snapshotItemRepository;
    private final ConfigurationApplyRepository applyRepository;
    private final ConfigurationApplyItemRepository applyItemRepository;
    private final DatabaseConfigurationApplyPortRegistry applyPortRegistry;
    private final ConfigurationValueComparator valueComparator;

    public ConfigurationApplyJobExecutor(ObjectMapper objectMapper,
            ManagedDatabaseRepository databaseRepository,
            ConfigurationApplyValidationService validationService,
            ConfigurationSnapshotService snapshotService,
            ConfigurationSnapshotItemRepository snapshotItemRepository,
            ConfigurationApplyRepository applyRepository,
            ConfigurationApplyItemRepository applyItemRepository,
            DatabaseConfigurationApplyPortRegistry applyPortRegistry,
            ConfigurationValueComparator valueComparator) {
        this.objectMapper = objectMapper;
        this.databaseRepository = databaseRepository;
        this.validationService = validationService;
        this.snapshotService = snapshotService;
        this.snapshotItemRepository = snapshotItemRepository;
        this.applyRepository = applyRepository;
        this.applyItemRepository = applyItemRepository;
        this.applyPortRegistry = applyPortRegistry;
        this.valueComparator = valueComparator;
    }

    @Transactional
    public ConfigurationApply execute(OperationJob job) {
        if (job == null) {
            throw new IllegalArgumentException("job is required.");
        }

        ConfigurationApplyJobPayload payload = parsePayload(job.getRequestPayload());

        validatePayload(payload);

        ManagedDatabase database = databaseRepository.findById(job.getTargetDatabaseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Database not found. databaseId=" + job.getTargetDatabaseId()));

        ConfigurationEngineType engineType = toConfigurationEngineType(database);

        ConfigurationApplyValidationResult validationResult =
                validationService.validate(database.getId(), toValidationRequest(payload));

        ConfigurationApply apply = ConfigurationApply.create(database.getId(), job.getId(),
                payload.requestedBy(), payload.reason(), validationResult.totalCount());

        ConfigurationApply savedApply = applyRepository.save(apply);

        List<ConfigurationApplyItem> applyItems =
                createApplyItems(savedApply.getId(), validationResult.items());

        List<ConfigurationApplyItem> savedApplyItems = applyItemRepository.saveAll(applyItems);

        ConfigurationSnapshot beforeSnapshot =
                snapshotService.collectSnapshot(database.getId(), engineType);

        savedApply.start(beforeSnapshot.getId());

        Map<String, ConfigurationSnapshotItem> beforeSnapshotItemMap =
                loadSnapshotItemMap(beforeSnapshot.getId());

        savedApplyItems.forEach(item -> item
                .markBeforeValue(findActualValue(beforeSnapshotItemMap, item.getParameterName())));

        DatabaseConfigurationApplyPort applyPort = applyPortRegistry.getApplyPort(engineType);

        for (ConfigurationApplyItem item : savedApplyItems) {
            applySingleItem(applyPort, database.getId(), item);
        }

        ConfigurationSnapshot afterSnapshot =
                snapshotService.collectSnapshot(database.getId(), engineType);

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

    private void applySingleItem(DatabaseConfigurationApplyPort applyPort, Long databaseId,
            ConfigurationApplyItem item) {
        try {
            ConfigurationApplyCommandResult commandResult =
                    applyPort.applyGlobalParameter(databaseId, item.getParameterName(),
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
        return snapshotItemRepository.findBySnapshotIdOrderByParameterNameAsc(snapshotId).stream()
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

    private ConfigurationEngineType toConfigurationEngineType(ManagedDatabase database) {
        if (database.getEngine() == null) {
            throw new IllegalArgumentException(
                    "Database engineType is required. databaseId=" + database.getId());
        }

        return ConfigurationEngineType.valueOf(database.getEngine().name());
    }

    private String normalizeParameterName(String parameterName) {
        return parameterName.trim().toLowerCase(Locale.ROOT);
    }
}
