package com.dbfleetops.policy.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration_apply")
public class ConfigurationApply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long databaseId;

    private Long operationJobId;

    private String requestedBy;

    private String reason;

    @Enumerated(EnumType.STRING)
    private ConfigurationApplyStatus status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer skippedCount;

    private Long beforeSnapshotId;

    private Long afterSnapshotId;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    protected ConfigurationApply() {}

    private ConfigurationApply(Long databaseId, Long operationJobId, String requestedBy,
            String reason, Integer totalCount) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        if (operationJobId == null) {
            throw new IllegalArgumentException("operationJobId is required.");
        }

        validateRequiredText(requestedBy, "requestedBy");

        this.databaseId = databaseId;
        this.operationJobId = operationJobId;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.status = ConfigurationApplyStatus.REQUESTED;
        this.totalCount = totalCount == null ? 0 : totalCount;
        this.successCount = 0;
        this.failedCount = 0;
        this.skippedCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public static ConfigurationApply create(Long databaseId, Long operationJobId,
            String requestedBy, String reason, Integer totalCount) {
        return new ConfigurationApply(databaseId, operationJobId, requestedBy, reason, totalCount);
    }

    public void start(Long beforeSnapshotId) {
        if (status != ConfigurationApplyStatus.REQUESTED) {
            throw new IllegalStateException(
                    "Only REQUESTED apply can be started. currentStatus=" + status);
        }

        if (beforeSnapshotId == null) {
            throw new IllegalArgumentException("beforeSnapshotId is required.");
        }

        this.beforeSnapshotId = beforeSnapshotId;
        this.status = ConfigurationApplyStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(Long afterSnapshotId, Integer successCount, Integer failedCount,
            Integer skippedCount) {
        if (status != ConfigurationApplyStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING apply can be completed. currentStatus=" + status);
        }

        if (afterSnapshotId == null) {
            throw new IllegalArgumentException("afterSnapshotId is required.");
        }

        int safeSuccessCount = safeCount(successCount);
        int safeFailedCount = safeCount(failedCount);
        int safeSkippedCount = safeCount(skippedCount);

        this.afterSnapshotId = afterSnapshotId;
        this.successCount = safeSuccessCount;
        this.failedCount = safeFailedCount;
        this.skippedCount = safeSkippedCount;
        this.status = determineCompletedStatus(safeSuccessCount, safeFailedCount, safeSkippedCount);
        this.completedAt = LocalDateTime.now();
    }

    public void fail(Integer successCount, Integer failedCount, Integer skippedCount) {
        int safeSuccessCount = safeCount(successCount);
        int safeFailedCount = safeCount(failedCount);
        int safeSkippedCount = safeCount(skippedCount);

        this.successCount = safeSuccessCount;
        this.failedCount = safeFailedCount;
        this.skippedCount = safeSkippedCount;

        if (safeSuccessCount > 0 && safeFailedCount > 0) {
            this.status = ConfigurationApplyStatus.PARTIALLY_SUCCEEDED;
        } else {
            this.status = ConfigurationApplyStatus.FAILED;
        }

        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == ConfigurationApplyStatus.SUCCEEDED
                || status == ConfigurationApplyStatus.FAILED
                || status == ConfigurationApplyStatus.PARTIALLY_SUCCEEDED) {
            throw new IllegalStateException(
                    "Completed apply cannot be cancelled. currentStatus=" + status);
        }

        this.status = ConfigurationApplyStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    private ConfigurationApplyStatus determineCompletedStatus(int successCount, int failedCount,
            int skippedCount) {
        if (failedCount > 0 && successCount > 0) {
            return ConfigurationApplyStatus.PARTIALLY_SUCCEEDED;
        }

        if (failedCount > 0) {
            return ConfigurationApplyStatus.FAILED;
        }

        if (successCount == 0 && skippedCount > 0) {
            return ConfigurationApplyStatus.FAILED;
        }

        return ConfigurationApplyStatus.SUCCEEDED;
    }

    private int safeCount(Integer count) {
        return count == null ? 0 : count;
    }

    private static void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public Long getOperationJobId() {
        return operationJobId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public ConfigurationApplyStatus getStatus() {
        return status;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public Long getBeforeSnapshotId() {
        return beforeSnapshotId;
    }

    public Long getAfterSnapshotId() {
        return afterSnapshotId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
