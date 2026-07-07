package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileParameter;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.dto.ConfigurationComparisonItem;
import com.dbfleetops.policy.dto.ConfigurationComparisonResult;
import com.dbfleetops.policy.infra.ConfigurationProfileParameterRepository;
import com.dbfleetops.policy.infra.ConfigurationProfileRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotItemRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConfigurationComparisonService {

    private final ConfigurationProfileRepository profileRepository;
    private final ConfigurationProfileParameterRepository parameterRepository;
    private final ConfigurationSnapshotRepository snapshotRepository;
    private final ConfigurationSnapshotItemRepository snapshotItemRepository;
    private final ConfigurationValueComparator valueComparator;

    public ConfigurationComparisonService(ConfigurationProfileRepository profileRepository,
            ConfigurationProfileParameterRepository parameterRepository,
            ConfigurationSnapshotRepository snapshotRepository,
            ConfigurationSnapshotItemRepository snapshotItemRepository,
            ConfigurationValueComparator valueComparator) {
        this.profileRepository = profileRepository;
        this.parameterRepository = parameterRepository;
        this.snapshotRepository = snapshotRepository;
        this.snapshotItemRepository = snapshotItemRepository;
        this.valueComparator = valueComparator;
    }

    @Transactional(readOnly = true)
    public ConfigurationComparisonResult compare(Long profileId, Long snapshotId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId is required.");
        }

        if (snapshotId == null) {
            throw new IllegalArgumentException("snapshotId is required.");
        }

        ConfigurationProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configuration profile not found. profileId=" + profileId));

        ConfigurationSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configuration snapshot not found. snapshotId=" + snapshotId));

        validateEngineType(profile, snapshot);

        List<ConfigurationProfileParameter> parameters =
                parameterRepository.findByProfileIdOrderByParameterNameAsc(profile.getId());

        List<ConfigurationSnapshotItem> snapshotItems =
                snapshotItemRepository.findBySnapshotIdOrderByParameterNameAsc(snapshot.getId());

        Map<String, ConfigurationSnapshotItem> snapshotItemMap = snapshotItems.stream()
                .collect(Collectors.toMap(item -> normalizeParameterName(item.getParameterName()),
                        Function.identity(), (first, second) -> first));

        List<ConfigurationComparisonItem> comparisonItems = parameters.stream()
                .map(parameter -> compareParameter(parameter, snapshotItemMap))
                .sorted(Comparator.comparing(ConfigurationComparisonItem::parameterName)).toList();

        return ConfigurationComparisonResult.of(profile.getId(), snapshot.getId(),
                snapshot.getDatabaseId(), snapshot.getEngineType(), comparisonItems);
    }

    private ConfigurationComparisonItem compareParameter(ConfigurationProfileParameter parameter,
            Map<String, ConfigurationSnapshotItem> snapshotItemMap) {
        ConfigurationSnapshotItem snapshotItem =
                snapshotItemMap.get(normalizeParameterName(parameter.getParameterName()));

        if (snapshotItem == null) {
            return new ConfigurationComparisonItem(parameter.getParameterName(),
                    parameter.getExpectedValue(), null, parameter.getValueType(),
                    parameter.getRequired(), parameter.getDynamic(), parameter.getApplyAllowed(),
                    ComplianceStatus.MISSING,
                    "Expected parameter is missing from actual snapshot.");
        }

        boolean matched = valueComparator.matches(parameter.getExpectedValue(),
                snapshotItem.getActualValue(), parameter.getValueType());

        if (matched) {
            return new ConfigurationComparisonItem(parameter.getParameterName(),
                    parameter.getExpectedValue(), snapshotItem.getActualValue(),
                    parameter.getValueType(), parameter.getRequired(), parameter.getDynamic(),
                    parameter.getApplyAllowed(), ComplianceStatus.COMPLIANT,
                    "Expected value matches actual value.");
        }

        return new ConfigurationComparisonItem(parameter.getParameterName(),
                parameter.getExpectedValue(), snapshotItem.getActualValue(),
                parameter.getValueType(), parameter.getRequired(), parameter.getDynamic(),
                parameter.getApplyAllowed(), ComplianceStatus.NON_COMPLIANT,
                "Expected " + parameter.getExpectedValue() + " but actual value is "
                        + snapshotItem.getActualValue() + ".");
    }

    private void validateEngineType(ConfigurationProfile profile, ConfigurationSnapshot snapshot) {
        if (profile.getEngineType() != snapshot.getEngineType()) {
            throw new IllegalArgumentException(
                    "Profile engine type and snapshot engine type do not match. profileEngineType="
                            + profile.getEngineType() + ", snapshotEngineType="
                            + snapshot.getEngineType());
        }
    }

    private String normalizeParameterName(String parameterName) {
        return parameterName.trim().toLowerCase();
    }
}
