package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ConfigurationDrift;
import com.dbfleetops.policy.domain.ConfigurationDriftStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;

import java.time.LocalDateTime;
import java.util.List;

public record ConfigurationDriftResponse(Long driftId, Long databaseId, Long profileId,
        Long snapshotId, ConfigurationEngineType engineType, ConfigurationDriftStatus status,
        Integer totalCount, Integer compliantCount, Integer nonCompliantCount, Integer missingCount,
        LocalDateTime checkedAt, List<ConfigurationDriftItemResponse> items) {

    public static ConfigurationDriftResponse from(ConfigurationDrift drift,
            List<ConfigurationDriftItemResponse> items) {
        return new ConfigurationDriftResponse(drift.getId(), drift.getDatabaseId(),
                drift.getProfileId(), drift.getSnapshotId(), drift.getEngineType(),
                drift.getStatus(), drift.getTotalCount(), drift.getCompliantCount(),
                drift.getNonCompliantCount(), drift.getMissingCount(), drift.getCheckedAt(), items);
    }

    public static ConfigurationDriftResponse from(ConfigurationDrift drift) {
        return from(drift, List.of());
    }
}
