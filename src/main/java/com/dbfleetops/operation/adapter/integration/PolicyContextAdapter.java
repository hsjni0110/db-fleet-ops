package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.policy.application.*;
import com.dbfleetops.policy.domain.*;
import com.dbfleetops.policy.dto.*;
import com.dbfleetops.policy.infra.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PolicyContextAdapter implements ConfigurationValidator, SnapshotCollector,
        ConfigurationComparator, DriftWriter, ConfigurationApplyStore,
        ConfigurationValueMatcher {
    private final ConfigurationApplyValidationService validator;
    private final ConfigurationSnapshotService snapshots;
    private final ConfigurationComparisonService comparator;
    private final ConfigurationDriftService drifts;
    private final ConfigurationApplyRepository applies;
    private final ConfigurationApplyItemRepository applyItems;
    private final ConfigurationSnapshotItemRepository snapshotItems;
    private final ConfigurationValueComparator valueComparator;
    public PolicyContextAdapter(ConfigurationApplyValidationService validator,
            ConfigurationSnapshotService snapshots, ConfigurationComparisonService comparator,
            ConfigurationDriftService drifts, ConfigurationApplyRepository applies,
            ConfigurationApplyItemRepository applyItems,
            ConfigurationSnapshotItemRepository snapshotItems,
            ConfigurationValueComparator valueComparator) {
        this.validator = validator; this.snapshots = snapshots; this.comparator = comparator;
        this.drifts = drifts; this.applies = applies; this.applyItems = applyItems;
        this.snapshotItems = snapshotItems; this.valueComparator = valueComparator;
    }
    public ConfigurationApplyValidationResult validate(Long id, CreateConfigurationApplyJobRequest request) {
        return validator.validate(id, request);
    }
    public ConfigurationSnapshot collect(Long id, ConfigurationEngineType type) {
        return snapshots.collectSnapshot(id, type);
    }
    public ConfigurationComparisonResult compare(Long profileId, Long snapshotId) {
        return comparator.compare(profileId, snapshotId);
    }
    public ConfigurationDriftResponse save(ConfigurationComparisonResult result) {
        return drifts.saveDrift(result);
    }
    public ConfigurationApply saveApply(ConfigurationApply apply) { return applies.save(apply); }
    public List<ConfigurationApplyItem> saveItems(List<ConfigurationApplyItem> items) {
        return applyItems.saveAll(items);
    }
    public List<ConfigurationSnapshotItem> findSnapshotItems(Long id) {
        return snapshotItems.findBySnapshotIdOrderByParameterNameAsc(id);
    }
    public boolean matches(String expected, String actual, ParameterValueType type) {
        return valueComparator.matches(expected, actual, type);
    }
}
