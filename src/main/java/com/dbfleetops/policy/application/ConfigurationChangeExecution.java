package com.dbfleetops.policy.application;

import com.dbfleetops.database.application.DatabaseConfigurationApplyPortRegistry;
import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyItem;
import com.dbfleetops.policy.domain.ConfigurationApplyItemStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationItem;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
import com.dbfleetops.policy.dto.ConfigurationChangeRequest;
import com.dbfleetops.policy.infra.ConfigurationApplyItemRepository;
import com.dbfleetops.policy.infra.ConfigurationApplyRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 검증된 설정값을 DB에 적용하고 적용 전후 값을 비교합니다. */
@Service
public class ConfigurationChangeExecution {

    private final ConfigurationApplyValidationService validation;
    private final ConfigurationSnapshotService snapshots;
    private final ConfigurationApplyRepository applies;
    private final ConfigurationApplyItemRepository applyItems;
    private final ConfigurationSnapshotItemRepository snapshotItems;
    private final DatabaseConfigurationApplyPortRegistry databaseCommands;
    private final ConfigurationValueComparator values;

    public ConfigurationChangeExecution(ConfigurationApplyValidationService validation,
            ConfigurationSnapshotService snapshots, ConfigurationApplyRepository applies,
            ConfigurationApplyItemRepository applyItems,
            ConfigurationSnapshotItemRepository snapshotItems,
            DatabaseConfigurationApplyPortRegistry databaseCommands,
            ConfigurationValueComparator values) {
        this.validation = validation;
        this.snapshots = snapshots;
        this.applies = applies;
        this.applyItems = applyItems;
        this.snapshotItems = snapshotItems;
        this.databaseCommands = databaseCommands;
        this.values = values;
    }

    @Transactional
    public void validate(Long databaseId, ConfigurationChangeRequest request) {
        validation.validate(databaseId, request);
    }

    @Transactional
    public ConfigurationApply apply(Long jobId, Long databaseId, ConfigurationEngineType engineType,
            ConfigurationChangeRequest request) {
        ConfigurationApplyValidationResult validationResult =
                validation.validate(databaseId, request);

        ConfigurationApply apply = ConfigurationApply.create(databaseId, jobId,
                request.requestedBy(), request.reason(), validationResult.totalCount());

        ConfigurationApply savedApply = applies.save(apply);

        List<ConfigurationApplyItem> applyItems =
                createApplyItems(savedApply.getId(), validationResult.items());

        List<ConfigurationApplyItem> savedApplyItems = this.applyItems.saveAll(applyItems);

        ConfigurationSnapshot beforeSnapshot =
                snapshots.collectSnapshot(databaseId, engineType);

        savedApply.start(beforeSnapshot.getId());

        Map<String, ConfigurationSnapshotItem> beforeSnapshotItemMap =
                loadSnapshotItemMap(beforeSnapshot.getId());

        savedApplyItems.forEach(item -> item
                .markBeforeValue(findActualValue(beforeSnapshotItemMap, item.getParameterName())));

        for (ConfigurationApplyItem item : savedApplyItems) {
            applySingleItem(engineType, databaseId, item);
        }

        ConfigurationSnapshot afterSnapshot =
                snapshots.collectSnapshot(databaseId, engineType);

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
                    databaseCommands.getApplyPort(engineType).applyGlobalParameter(
                            databaseId, item.getParameterName(), item.getRequestedValue(),
                            item.getValueType());

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

            boolean matched = values.matches(item.getRequestedValue(), afterValue,
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
        return snapshotItems.findBySnapshotIdOrderByParameterNameAsc(snapshotId).stream()
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

    private String normalizeParameterName(String parameterName) {
        return parameterName.trim().toLowerCase(Locale.ROOT);
    }
}
