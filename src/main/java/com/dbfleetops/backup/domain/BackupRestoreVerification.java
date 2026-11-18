package com.dbfleetops.backup.domain;

import lombok.Getter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "backup_restore_verification",
        indexes = {
                @Index(name = "idx_restore_verification_operation_job_id",
                        columnList = "operationJobId"),
                @Index(name = "idx_restore_verification_database_id", columnList = "databaseId"),
                @Index(name = "idx_restore_verification_status", columnList = "status")})
public class BackupRestoreVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long operationJobId;

    private Long backupTaskId;

    private Long restoreVerifyTaskId;

    private Long databaseId;

    private String sourceDatabaseName;

    @Column(length = 1000)
    private String backupFile;

    private String temporaryDatabaseName;

    @Enumerated(EnumType.STRING)
    private BackupRestoreVerificationStatus status;

    private Integer restoredTableCount;

    private Integer checkedTableCount;

    private Long totalRowCount;

    @Enumerated(EnumType.STRING)
    private TechnicalVerificationStatus technicalStatus;

    private String businessVerificationProfileKey;

    @Enumerated(EnumType.STRING)
    private BusinessVerificationStatus businessStatus;

    private Integer checkedRuleCount;

    private Integer failedRuleCount;

    @Enumerated(EnumType.STRING)
    private CleanupStatus cleanupStatus;

    private String cleanupErrorCode;

    @Column(length = 2000)
    private String cleanupErrorMessage;

    private String errorCode;

    @Column(length = 2000)
    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    protected BackupRestoreVerification() {}

    private BackupRestoreVerification(Long operationJobId, Long backupTaskId,
            Long restoreVerifyTaskId, Long databaseId, String sourceDatabaseName, String backupFile,
            String temporaryDatabaseName) {
        validateRequired(operationJobId, backupTaskId, restoreVerifyTaskId, databaseId,
                sourceDatabaseName, backupFile, temporaryDatabaseName);

        this.operationJobId = operationJobId;
        this.backupTaskId = backupTaskId;
        this.restoreVerifyTaskId = restoreVerifyTaskId;
        this.databaseId = databaseId;
        this.sourceDatabaseName = sourceDatabaseName;
        this.backupFile = backupFile;
        this.temporaryDatabaseName = temporaryDatabaseName;
        this.status = BackupRestoreVerificationStatus.REQUESTED;
        this.restoredTableCount = 0;
        this.checkedTableCount = 0;
        this.totalRowCount = 0L;
        this.businessStatus = BusinessVerificationStatus.NOT_APPLIED;
        this.checkedRuleCount = 0;
        this.failedRuleCount = 0;
        this.cleanupStatus = CleanupStatus.NOT_REQUESTED;
        this.createdAt = LocalDateTime.now();
    }

    public static BackupRestoreVerification create(Long operationJobId, Long backupTaskId,
            Long restoreVerifyTaskId, Long databaseId, String sourceDatabaseName, String backupFile,
            String temporaryDatabaseName) {
        return new BackupRestoreVerification(operationJobId, backupTaskId, restoreVerifyTaskId,
                databaseId, sourceDatabaseName, backupFile, temporaryDatabaseName);
    }

    public void start() {
        if (status != BackupRestoreVerificationStatus.REQUESTED) {
            throw new IllegalStateException(
                    "Only REQUESTED verification can be started. currentStatus=" + status);
        }

        this.status = BackupRestoreVerificationStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void verify(Integer restoredTableCount, Integer checkedTableCount, Long totalRowCount) {
        if (status != BackupRestoreVerificationStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING verification can be verified. currentStatus=" + status);
        }

        validateCounts(restoredTableCount, checkedTableCount, totalRowCount);

        this.status = BackupRestoreVerificationStatus.VERIFIED;
        this.restoredTableCount = restoredTableCount;
        this.checkedTableCount = checkedTableCount;
        this.totalRowCount = totalRowCount;
        this.technicalStatus = TechnicalVerificationStatus.VERIFIED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, String errorMessage) {
        if (status == BackupRestoreVerificationStatus.VERIFIED) {
            throw new IllegalStateException("VERIFIED verification cannot be failed.");
        }

        this.status = BackupRestoreVerificationStatus.FAILED;
        if (this.technicalStatus == null) {
            this.technicalStatus = TechnicalVerificationStatus.FAILED;
        }
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void cleanupFailed(String errorCode, String errorMessage) {
        if (status != BackupRestoreVerificationStatus.RUNNING
                && status != BackupRestoreVerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "Only RUNNING or VERIFIED verification can be marked cleanup failed. currentStatus="
                            + status);
        }

        this.status = BackupRestoreVerificationStatus.CLEANUP_FAILED;
        this.cleanupStatus = CleanupStatus.FAILED;
        this.cleanupErrorCode = errorCode;
        this.cleanupErrorMessage = errorMessage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void complete(TechnicalVerificationStatus technicalStatus,
            String businessVerificationProfileKey, BusinessVerificationStatus businessStatus,
            Integer checkedRuleCount, Integer failedRuleCount, CleanupStatus cleanupStatus,
            Integer restoredTableCount, Integer checkedTableCount, Long totalRowCount,
            String errorCode, String errorMessage, String cleanupErrorCode,
            String cleanupErrorMessage) {
        if (status != BackupRestoreVerificationStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING verification can be completed. currentStatus=" + status);
        }
        if (technicalStatus == null || businessStatus == null || cleanupStatus == null) {
            throw new IllegalArgumentException("Verification phase statuses are required.");
        }
        validateCounts(restoredTableCount, checkedTableCount, totalRowCount);
        if (checkedRuleCount == null || checkedRuleCount < 0 || failedRuleCount == null
                || failedRuleCount < 0 || failedRuleCount > checkedRuleCount) {
            throw new IllegalArgumentException("Business Rule counts are invalid.");
        }

        this.technicalStatus = technicalStatus;
        this.businessVerificationProfileKey = normalize(businessVerificationProfileKey);
        this.businessStatus = businessStatus;
        this.checkedRuleCount = checkedRuleCount;
        this.failedRuleCount = failedRuleCount;
        this.cleanupStatus = cleanupStatus;
        this.restoredTableCount = restoredTableCount;
        this.checkedTableCount = checkedTableCount;
        this.totalRowCount = totalRowCount;
        this.cleanupErrorCode = cleanupErrorCode;
        this.cleanupErrorMessage = cleanupErrorMessage;

        if (technicalStatus == TechnicalVerificationStatus.FAILED
                || businessStatus == BusinessVerificationStatus.FAILED
                || businessStatus == BusinessVerificationStatus.ERROR
                || businessStatus == BusinessVerificationStatus.SKIPPED) {
            this.status = BackupRestoreVerificationStatus.FAILED;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        } else if (cleanupStatus == CleanupStatus.FAILED) {
            this.status = BackupRestoreVerificationStatus.CLEANUP_FAILED;
            this.errorCode = cleanupErrorCode;
            this.errorMessage = cleanupErrorMessage;
        } else {
            this.status = BackupRestoreVerificationStatus.VERIFIED;
            this.errorCode = null;
            this.errorMessage = null;
        }
        this.completedAt = LocalDateTime.now();
    }

    private void validateRequired(Long operationJobId, Long backupTaskId, Long restoreVerifyTaskId,
            Long databaseId, String sourceDatabaseName, String backupFile,
            String temporaryDatabaseName) {
        if (operationJobId == null) {
            throw new IllegalArgumentException("operationJobId is required.");
        }

        if (backupTaskId == null) {
            throw new IllegalArgumentException("backupTaskId is required.");
        }

        if (restoreVerifyTaskId == null) {
            throw new IllegalArgumentException("restoreVerifyTaskId is required.");
        }

        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        if (sourceDatabaseName == null || sourceDatabaseName.isBlank()) {
            throw new IllegalArgumentException("sourceDatabaseName is required.");
        }

        if (backupFile == null || backupFile.isBlank()) {
            throw new IllegalArgumentException("backupFile is required.");
        }

        if (temporaryDatabaseName == null || temporaryDatabaseName.isBlank()) {
            throw new IllegalArgumentException("temporaryDatabaseName is required.");
        }
    }

    private void validateCounts(Integer restoredTableCount, Integer checkedTableCount,
            Long totalRowCount) {
        if (restoredTableCount == null || restoredTableCount < 0) {
            throw new IllegalArgumentException("restoredTableCount must be zero or positive.");
        }

        if (checkedTableCount == null || checkedTableCount < 0) {
            throw new IllegalArgumentException("checkedTableCount must be zero or positive.");
        }

        if (totalRowCount == null || totalRowCount < 0) {
            throw new IllegalArgumentException("totalRowCount must be zero or positive.");
        }

        if (checkedTableCount > restoredTableCount) {
            throw new IllegalArgumentException(
                    "checkedTableCount cannot exceed restoredTableCount. restoredTableCount="
                            + restoredTableCount + ", checkedTableCount=" + checkedTableCount);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

















}
