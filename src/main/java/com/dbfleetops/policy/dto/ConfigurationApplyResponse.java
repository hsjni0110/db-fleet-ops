package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ConfigurationApply;
import com.dbfleetops.policy.domain.ConfigurationApplyStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ConfigurationApplyResponse(Long applyId, Long databaseId, Long operationJobId,
        String requestedBy, String reason, ConfigurationApplyStatus status, Integer totalCount,
        Integer successCount, Integer failedCount, Integer skippedCount, Long beforeSnapshotId,
        Long afterSnapshotId, LocalDateTime createdAt, LocalDateTime startedAt,
        LocalDateTime completedAt, List<ConfigurationApplyItemResponse> items) {

    public static ConfigurationApplyResponse from(ConfigurationApply apply) {
        return from(apply, List.of());
    }

    public static ConfigurationApplyResponse from(ConfigurationApply apply,
            List<ConfigurationApplyItemResponse> items) {
        return new ConfigurationApplyResponse(apply.getId(), apply.getDatabaseId(),
                apply.getOperationJobId(), apply.getRequestedBy(), apply.getReason(),
                apply.getStatus(), apply.getTotalCount(), apply.getSuccessCount(),
                apply.getFailedCount(), apply.getSkippedCount(), apply.getBeforeSnapshotId(),
                apply.getAfterSnapshotId(), apply.getCreatedAt(), apply.getStartedAt(),
                apply.getCompletedAt(), items == null ? List.of() : items);
    }
}
