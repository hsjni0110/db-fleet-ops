package com.dbfleetops.backup.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "backup_restore_verification_item", indexes = {
        @Index(name = "idx_restore_verification_item_verification_id",
                columnList = "verificationId"),
        @Index(name = "idx_restore_verification_item_table_name", columnList = "tableName")})
public class BackupRestoreVerificationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long verificationId;

    private String tableName;

    private Boolean existsInRestoredDb;

    private Long rowCount;

    @Enumerated(EnumType.STRING)
    private BackupRestoreVerificationItemStatus status;

    @Column(length = 2000)
    private String message;

    private LocalDateTime createdAt;

    protected BackupRestoreVerificationItem() {}

    private BackupRestoreVerificationItem(Long verificationId, String tableName,
            Boolean existsInRestoredDb, Long rowCount, BackupRestoreVerificationItemStatus status,
            String message) {
        if (verificationId == null) {
            throw new IllegalArgumentException("verificationId is required.");
        }

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName is required.");
        }

        if (existsInRestoredDb == null) {
            throw new IllegalArgumentException("existsInRestoredDb is required.");
        }

        if (status == null) {
            throw new IllegalArgumentException("status is required.");
        }

        if (rowCount != null && rowCount < 0) {
            throw new IllegalArgumentException("rowCount must be zero or positive.");
        }

        this.verificationId = verificationId;
        this.tableName = tableName;
        this.existsInRestoredDb = existsInRestoredDb;
        this.rowCount = rowCount;
        this.status = status;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public static BackupRestoreVerificationItem verified(Long verificationId, String tableName,
            Long rowCount) {
        return new BackupRestoreVerificationItem(verificationId, tableName, true, rowCount,
                BackupRestoreVerificationItemStatus.VERIFIED, "table verified");
    }

    public static BackupRestoreVerificationItem missing(Long verificationId, String tableName,
            String message) {
        return new BackupRestoreVerificationItem(verificationId, tableName, false, null,
                BackupRestoreVerificationItemStatus.MISSING, message);
    }

    public static BackupRestoreVerificationItem countFailed(Long verificationId, String tableName,
            String message) {
        return new BackupRestoreVerificationItem(verificationId, tableName, true, null,
                BackupRestoreVerificationItemStatus.COUNT_FAILED, message);
    }

    public static BackupRestoreVerificationItem skipped(Long verificationId, String tableName,
            String message) {
        return new BackupRestoreVerificationItem(verificationId, tableName, true, null,
                BackupRestoreVerificationItemStatus.SKIPPED, message);
    }

    public Long getId() {
        return id;
    }

    public Long getVerificationId() {
        return verificationId;
    }

    public String getTableName() {
        return tableName;
    }

    public Boolean getExistsInRestoredDb() {
        return existsInRestoredDb;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public BackupRestoreVerificationItemStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
