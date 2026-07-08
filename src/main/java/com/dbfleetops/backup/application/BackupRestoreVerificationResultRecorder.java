package com.dbfleetops.backup.application;

import com.dbfleetops.backup.domain.BackupRestoreVerification;
import com.dbfleetops.backup.domain.BackupRestoreVerificationItem;
import com.dbfleetops.backup.infra.BackupRestoreVerificationItemRepository;
import com.dbfleetops.backup.infra.BackupRestoreVerificationRepository;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.dto.MysqlRestoreVerifyTaskItemResultPayload;
import com.dbfleetops.operation.dto.MysqlRestoreVerifyTaskResultPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BackupRestoreVerificationResultRecorder {

    private final BackupRestoreVerificationRepository verificationRepository;
    private final BackupRestoreVerificationItemRepository itemRepository;
    private final ObjectMapper objectMapper;

    public BackupRestoreVerificationResultRecorder(
            BackupRestoreVerificationRepository verificationRepository,
            BackupRestoreVerificationItemRepository itemRepository, ObjectMapper objectMapper) {
        this.verificationRepository = verificationRepository;
        this.itemRepository = itemRepository;
        this.objectMapper = objectMapper;
    }

    public MysqlRestoreVerifyTaskResultPayload record(OperationTask restoreVerifyTask,
            String resultPayloadJson) {
        MysqlRestoreVerifyTaskResultPayload resultPayload = parseResultPayload(resultPayloadJson);

        validateResultPayload(restoreVerifyTask, resultPayload);

        BackupRestoreVerification verification =
                BackupRestoreVerification.create(restoreVerifyTask.getOperationJobId(),
                        resultPayload.backupTaskId(), restoreVerifyTask.getId(),
                        resultPayload.databaseId(), resultPayload.sourceDatabaseName(),
                        resultPayload.backupFile(), resultPayload.temporaryDatabaseName());

        verification.start();

        if (resultPayload.isVerified()) {
            verification.verify(safeInteger(resultPayload.restoredTableCount()),
                    safeInteger(resultPayload.checkedTableCount()),
                    safeLong(resultPayload.totalRowCount()));
        } else if (resultPayload.isCleanupFailed()) {
            verification
                    .cleanupFailed(defaultErrorCode(resultPayload.errorCode(), "CLEANUP_FAILED"),
                            defaultErrorMessage(resultPayload.errorMessage(),
                                    resultPayload.message(),
                                    "restore verification completed but cleanup failed"));
        } else {
            verification.fail(defaultErrorCode(resultPayload.errorCode(), "RESTORE_VERIFY_FAILED"),
                    defaultErrorMessage(resultPayload.errorMessage(), resultPayload.message(),
                            "restore verification failed"));
        }

        BackupRestoreVerification savedVerification = verificationRepository.save(verification);

        saveItems(savedVerification.getId(), resultPayload.items());

        return resultPayload;
    }

    private MysqlRestoreVerifyTaskResultPayload parseResultPayload(String resultPayloadJson) {
        if (resultPayloadJson == null || resultPayloadJson.isBlank()) {
            throw new IllegalArgumentException(
                    "Restore verify task resultPayloadJson is required.");
        }

        try {
            return objectMapper.readValue(resultPayloadJson,
                    MysqlRestoreVerifyTaskResultPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid restore verify task resultPayloadJson.",
                    exception);
        }
    }

    private void validateResultPayload(OperationTask restoreVerifyTask,
            MysqlRestoreVerifyTaskResultPayload resultPayload) {
        if (restoreVerifyTask.getOperationJobId() == null) {
            throw new IllegalArgumentException(
                    "Restore verify task must be linked to operation job.");
        }

        if (resultPayload.status() == null || resultPayload.status().isBlank()) {
            throw new IllegalArgumentException("Restore verify status is required.");
        }

        if (resultPayload.databaseId() == null) {
            throw new IllegalArgumentException(
                    "databaseId is required in restore verify result payload.");
        }

        if (resultPayload.backupTaskId() == null) {
            throw new IllegalArgumentException(
                    "backupTaskId is required in restore verify result payload.");
        }

        if (resultPayload.sourceDatabaseName() == null
                || resultPayload.sourceDatabaseName().isBlank()) {
            throw new IllegalArgumentException(
                    "sourceDatabaseName is required in restore verify result payload.");
        }

        if (resultPayload.backupFile() == null || resultPayload.backupFile().isBlank()) {
            throw new IllegalArgumentException(
                    "backupFile is required in restore verify result payload.");
        }

        if (resultPayload.temporaryDatabaseName() == null
                || resultPayload.temporaryDatabaseName().isBlank()) {
            throw new IllegalArgumentException(
                    "temporaryDatabaseName is required in restore verify result payload.");
        }
    }

    private void saveItems(Long verificationId,
            List<MysqlRestoreVerifyTaskItemResultPayload> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (MysqlRestoreVerifyTaskItemResultPayload item : items) {
            itemRepository.save(toEntity(verificationId, item));
        }
    }

    private BackupRestoreVerificationItem toEntity(Long verificationId,
            MysqlRestoreVerifyTaskItemResultPayload item) {
        String status = item.status() == null ? "" : item.status().toUpperCase();

        return switch (status) {
            case "VERIFIED" -> BackupRestoreVerificationItem.verified(verificationId,
                    item.tableName(), safeLong(item.rowCount()));

            case "MISSING" -> BackupRestoreVerificationItem.missing(verificationId,
                    item.tableName(), defaultMessage(item.message(),
                            "expected table is missing in restored database"));

            case "COUNT_FAILED" -> BackupRestoreVerificationItem.countFailed(verificationId,
                    item.tableName(),
                    defaultMessage(item.message(), "failed to count rows in restored table"));

            case "SKIPPED" -> BackupRestoreVerificationItem.skipped(verificationId,
                    item.tableName(),
                    defaultMessage(item.message(), "row count verification skipped"));

            default -> BackupRestoreVerificationItem.skipped(verificationId, item.tableName(),
                    defaultMessage(item.message(),
                            "unsupported restore verification item status: " + item.status()));
        };
    }

    private Integer safeInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private Long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String defaultErrorCode(String errorCode, String defaultValue) {
        if (errorCode == null || errorCode.isBlank()) {
            return defaultValue;
        }

        return errorCode;
    }

    private String defaultErrorMessage(String errorMessage, String message, String defaultValue) {
        if (errorMessage != null && !errorMessage.isBlank()) {
            return errorMessage;
        }

        if (message != null && !message.isBlank()) {
            return message;
        }

        return defaultValue;
    }

    private String defaultMessage(String message, String defaultValue) {
        if (message == null || message.isBlank()) {
            return defaultValue;
        }

        return message;
    }
}
