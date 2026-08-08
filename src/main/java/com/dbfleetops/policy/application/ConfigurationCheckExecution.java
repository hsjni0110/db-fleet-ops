package com.dbfleetops.policy.application;

import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.dto.ConfigurationComparisonResult;
import com.dbfleetops.policy.dto.ConfigurationDriftResponse;
import org.springframework.stereotype.Service;

/** 현재 DB 설정을 수집하고 설정 기준과 비교한 결과를 저장합니다. */
@Service
public class ConfigurationCheckExecution {

    private final ConfigurationSnapshotService snapshots;
    private final ConfigurationComparisonService comparisons;
    private final ConfigurationDriftService drifts;

    public ConfigurationCheckExecution(ConfigurationSnapshotService snapshots,
            ConfigurationComparisonService comparisons, ConfigurationDriftService drifts) {
        this.snapshots = snapshots;
        this.comparisons = comparisons;
        this.drifts = drifts;
    }

    public ConfigurationDriftResponse check(Long databaseId, ConfigurationEngineType engineType,
            Long profileId) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }
        if (engineType == null) {
            throw new IllegalArgumentException("engineType is required.");
        }
        if (profileId == null) {
            throw new IllegalArgumentException("profileId is required.");
        }

        ConfigurationSnapshot snapshot = snapshots.collectSnapshot(databaseId, engineType);

        ConfigurationComparisonResult comparisonResult =
                comparisons.compare(profileId, snapshot.getId());

        return drifts.saveDrift(comparisonResult);
    }
}
