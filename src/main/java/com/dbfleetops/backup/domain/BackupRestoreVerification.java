package com.dbfleetops.backup.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

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
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, String errorMessage) {
        if (status == BackupRestoreVerificationStatus.VERIFIED) {
            throw new IllegalStateException("VERIFIED verification cannot be failed.");
        }

        this.status = BackupRestoreVerificationStatus.FAILED;
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
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
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

    public Long getId() {
        return id;
    }

    public Long getOperationJobId() {
        return operationJobId;
    }

    public Long getBackupTaskId() {
        return backupTaskId;
    }

    public Long getRestoreVerifyTaskId() {
        return restoreVerifyTaskId;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public String getSourceDatabaseName() {
        return sourceDatabaseName;
    }

    public String getBackupFile() {
        return backupFile;
    }

    public String getTemporaryDatabaseName() {
        return temporaryDatabaseName;
    }

    public BackupRestoreVerificationStatus getStatus() {
        return status;
    }

    public Integer getRestoredTableCount() {
        return restoredTableCount;
    }

    public Integer getCheckedTableCount() {
        return checkedTableCount;
    }

    public Long getTotalRowCount() {
        return totalRowCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
