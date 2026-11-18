package com.dbfleetops.backup.application;

import com.dbfleetops.backup.domain.BackupRestoreVerification;
import com.dbfleetops.backup.domain.BackupRestoreVerificationItem;
import com.dbfleetops.backup.domain.BusinessRuleResult;
import com.dbfleetops.backup.domain.BusinessRuleStatus;
import com.dbfleetops.backup.domain.BusinessVerificationStatus;
import com.dbfleetops.backup.domain.CleanupStatus;
import com.dbfleetops.backup.domain.TechnicalVerificationStatus;
import com.dbfleetops.backup.infra.BusinessRuleResultRepository;
import com.dbfleetops.backup.infra.BackupRestoreVerificationItemRepository;
import com.dbfleetops.backup.infra.BackupRestoreVerificationRepository;
import com.dbfleetops.backup.dto.BusinessRuleResultPayload;
import com.dbfleetops.backup.dto.BusinessVerificationResultPayload;
import com.dbfleetops.backup.dto.RestoreVerificationItemResult;
import com.dbfleetops.backup.dto.RestoreVerificationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BackupRestoreVerificationResultRecorder {

    private final BackupRestoreVerificationRepository verificationRepository;
    private final BackupRestoreVerificationItemRepository itemRepository;
    private final BusinessRuleResultRepository ruleResultRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public BackupRestoreVerificationResultRecorder(
            BackupRestoreVerificationRepository verificationRepository,
            BackupRestoreVerificationItemRepository itemRepository,
            BusinessRuleResultRepository ruleResultRepository, ObjectMapper objectMapper) {
        this.verificationRepository = verificationRepository;
        this.itemRepository = itemRepository;
        this.ruleResultRepository = ruleResultRepository;
        this.objectMapper = objectMapper;
    }

    public BackupRestoreVerificationResultRecorder(
            BackupRestoreVerificationRepository verificationRepository,
            BackupRestoreVerificationItemRepository itemRepository, ObjectMapper objectMapper) {
        this(verificationRepository, itemRepository, null, objectMapper);
    }

    public RestoreVerificationResult record(Long operationJobId, Long restoreVerifyTaskId,
            String resultPayloadJson) {
        RestoreVerificationResult resultPayload = parseResultPayload(resultPayloadJson);

        validateResultPayload(operationJobId, resultPayload);

        BackupRestoreVerification verification =
                BackupRestoreVerification.create(operationJobId,
                        resultPayload.backupTaskId(), restoreVerifyTaskId,
                        resultPayload.databaseId(), resultPayload.sourceDatabaseName(),
                        resultPayload.backupFile(), resultPayload.temporaryDatabaseName());

        verification.start();

        BusinessVerificationResultPayload business = resultPayload.businessVerification();
        TechnicalVerificationStatus technicalStatus = technicalStatus(resultPayload);
        BusinessVerificationStatus businessStatus = businessStatus(business);
        CleanupStatus cleanupStatus = cleanupStatus(resultPayload);

        verification.complete(technicalStatus, business == null ? null : business.profileKey(),
                businessStatus, business == null ? 0 : safeInteger(business.checkedRuleCount()),
                business == null ? 0 : safeInteger(business.failedRuleCount()), cleanupStatus,
                safeInteger(resultPayload.restoredTableCount()),
                safeInteger(resultPayload.checkedTableCount()),
                safeLong(resultPayload.totalRowCount()), errorCode(resultPayload, businessStatus),
                defaultErrorMessage(resultPayload.errorMessage(), resultPayload.message(),
                        "restore verification failed"), resultPayload.cleanupErrorCode(),
                resultPayload.cleanupErrorMessage());

        BackupRestoreVerification savedVerification = verificationRepository.save(verification);

        saveItems(savedVerification.getId(), resultPayload.items());
        saveRuleResults(savedVerification.getId(), business);

        return resultPayload;
    }

    private void saveRuleResults(Long verificationId,
            BusinessVerificationResultPayload business) {
        if (ruleResultRepository == null || business == null || business.ruleResults() == null) {
            return;
        }
        for (BusinessRuleResultPayload result : business.ruleResults()) {
            ruleResultRepository.save(BusinessRuleResult.create(verificationId, result.code(),
                    result.name(), parseRuleStatus(result.status()), result.violationCount(),
                    safeLong(result.durationMillis()), result.message()));
        }
    }

    private BusinessRuleStatus parseRuleStatus(String status) {
        try {
            return BusinessRuleStatus.valueOf(status == null ? "" : status.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported Business Rule status: " + status,
                    exception);
        }
    }

    private TechnicalVerificationStatus technicalStatus(RestoreVerificationResult result) {
        if (result.technicalStatus() != null) {
            return result.technicalStatus();
        }
        return result.isVerified() || result.isCleanupFailed()
                ? TechnicalVerificationStatus.VERIFIED : TechnicalVerificationStatus.FAILED;
    }

    private BusinessVerificationStatus businessStatus(BusinessVerificationResultPayload business) {
        if (business == null || business.status() == null || business.status().isBlank()) {
            return BusinessVerificationStatus.NOT_APPLIED;
        }
        try {
            return BusinessVerificationStatus.valueOf(business.status().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported Business Verification status: " + business.status(), exception);
        }
    }

    private CleanupStatus cleanupStatus(RestoreVerificationResult result) {
        if (result.cleanupStatus() != null) {
            return result.cleanupStatus();
        }
        return result.isCleanupFailed() ? CleanupStatus.FAILED : CleanupStatus.COMPLETED;
    }

    private String errorCode(RestoreVerificationResult result,
            BusinessVerificationStatus businessStatus) {
        if (result.errorCode() != null && !result.errorCode().isBlank()) {
            return result.errorCode();
        }
        if (businessStatus == BusinessVerificationStatus.ERROR) {
            return "BUSINESS_VERIFICATION_ERROR";
        }
        if (businessStatus == BusinessVerificationStatus.FAILED) {
            return "BUSINESS_VERIFICATION_FAILED";
        }
        return result.isCleanupFailed() ? "CLEANUP_FAILED" : "RESTORE_VERIFY_FAILED";
    }

    private RestoreVerificationResult parseResultPayload(String resultPayloadJson) {
        if (resultPayloadJson == null || resultPayloadJson.isBlank()) {
            throw new IllegalArgumentException(
                    "Restore verify task resultPayloadJson is required.");
        }

        try {
            return objectMapper.readValue(resultPayloadJson,
                    RestoreVerificationResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid restore verify task resultPayloadJson.",
                    exception);
        }
    }

    private void validateResultPayload(Long operationJobId,
            RestoreVerificationResult resultPayload) {
        if (operationJobId == null) {
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
            List<RestoreVerificationItemResult> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (RestoreVerificationItemResult item : items) {
            itemRepository.save(toEntity(verificationId, item));
        }
    }

    private BackupRestoreVerificationItem toEntity(Long verificationId,
            RestoreVerificationItemResult item) {
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
