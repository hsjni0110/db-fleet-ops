package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ConfigurationDrift;
import com.dbfleetops.policy.domain.ConfigurationDriftItem;
import com.dbfleetops.policy.domain.ConfigurationDriftStatus;
import com.dbfleetops.policy.dto.ConfigurationComparisonItem;
import com.dbfleetops.policy.dto.ConfigurationComparisonResult;
import com.dbfleetops.policy.dto.ConfigurationDriftItemResponse;
import com.dbfleetops.policy.dto.ConfigurationDriftResponse;
import com.dbfleetops.policy.infra.ConfigurationDriftItemRepository;
import com.dbfleetops.policy.infra.ConfigurationDriftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConfigurationDriftService {

    private final ConfigurationDriftRepository driftRepository;
    private final ConfigurationDriftItemRepository driftItemRepository;

    public ConfigurationDriftService(ConfigurationDriftRepository driftRepository,
            ConfigurationDriftItemRepository driftItemRepository) {
        this.driftRepository = driftRepository;
        this.driftItemRepository = driftItemRepository;
    }

    @Transactional
    public ConfigurationDriftResponse saveDrift(ConfigurationComparisonResult comparisonResult) {
        if (comparisonResult == null) {
            throw new IllegalArgumentException("comparisonResult is required.");
        }

        ConfigurationDrift drift = ConfigurationDrift.create(comparisonResult.databaseId(),
                comparisonResult.profileId(), comparisonResult.snapshotId(),
                comparisonResult.engineType(), toDriftStatus(comparisonResult.overallStatus()),
                comparisonResult.totalCount(), comparisonResult.compliantCount(),
                comparisonResult.nonCompliantCount(), comparisonResult.missingCount());

        ConfigurationDrift savedDrift = driftRepository.save(drift);

        List<ConfigurationDriftItem> driftItems = comparisonResult.items().stream()
                .map(item -> toDriftItem(savedDrift.getId(), item)).toList();

        List<ConfigurationDriftItem> savedItems = driftItemRepository.saveAll(driftItems);

        List<ConfigurationDriftItemResponse> itemResponses =
                savedItems.stream().map(ConfigurationDriftItemResponse::from).toList();

        return ConfigurationDriftResponse.from(savedDrift, itemResponses);
    }

    @Transactional(readOnly = true)
    public ConfigurationDriftResponse getDrift(Long driftId) {
        if (driftId == null) {
            throw new IllegalArgumentException("driftId is required.");
        }

        ConfigurationDrift drift =
                driftRepository.findById(driftId).orElseThrow(() -> new IllegalArgumentException(
                        "Configuration drift not found. driftId=" + driftId));

        List<ConfigurationDriftItemResponse> items =
                driftItemRepository.findByDriftIdOrderByParameterNameAsc(driftId).stream()
                        .map(ConfigurationDriftItemResponse::from).toList();

        return ConfigurationDriftResponse.from(drift, items);
    }

    @Transactional(readOnly = true)
    public ConfigurationDriftResponse getLatestDrift(Long databaseId) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        ConfigurationDrift drift =
                driftRepository.findFirstByDatabaseIdOrderByCheckedAtDesc(databaseId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Configuration drift not found. databaseId=" + databaseId));

        return getDrift(drift.getId());
    }

    private ConfigurationDriftItem toDriftItem(Long driftId, ConfigurationComparisonItem item) {
        return ConfigurationDriftItem.create(driftId, item.parameterName(), item.expectedValue(),
                item.actualValue(), item.valueType(), item.required(), item.dynamic(),
                item.applyAllowed(), item.complianceStatus(), item.message());
    }

    private ConfigurationDriftStatus toDriftStatus(ComplianceStatus overallStatus) {
        if (overallStatus == ComplianceStatus.COMPLIANT) {
            return ConfigurationDriftStatus.COMPLIANT;
        }

        return ConfigurationDriftStatus.NON_COMPLIANT;
    }
}
