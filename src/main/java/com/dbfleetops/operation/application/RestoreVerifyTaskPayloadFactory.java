package com.dbfleetops.operation.application;

import com.dbfleetops.operation.dto.MysqlBackupTaskPayload;
import com.dbfleetops.operation.dto.MysqlBackupTaskResultPayload;
import com.dbfleetops.operation.dto.MysqlRestoreVerifyTaskPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RestoreVerifyTaskPayloadFactory {

    private final ObjectMapper objectMapper;

    public RestoreVerifyTaskPayloadFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createRestoreVerifyTaskPayloadJson(Long restoreVerifyTaskOperationJobId,
            Long backupTaskId, String backupTaskParametersJson,
            String backupTaskResultPayloadJson) {
        MysqlBackupTaskPayload backupTaskPayload = parseBackupTaskPayload(backupTaskParametersJson);

        MysqlBackupTaskResultPayload backupResultPayload =
                parseBackupTaskResultPayload(backupTaskResultPayloadJson);

        validateBackupResultPayload(backupResultPayload);

        MysqlRestoreVerifyTaskPayload restoreVerifyTaskPayload = new MysqlRestoreVerifyTaskPayload(
                restoreVerifyTaskOperationJobId, backupTaskPayload.databaseId(), backupTaskId,
                backupTaskPayload.databaseName(), backupResultPayload.backupFile(),
                backupTaskPayload.host(), backupTaskPayload.port(), backupTaskPayload.username(),
                backupTaskPayload.password(),
                buildTemporaryDatabaseName(backupTaskPayload.databaseName(),
                        restoreVerifyTaskOperationJobId),
                backupTaskPayload.expectedTables(), backupTaskPayload.shouldVerifyRowCount(),
                backupTaskPayload.shouldCleanup());

        try {
            return objectMapper.writeValueAsString(restoreVerifyTaskPayload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize restore verify task payload.",
                    exception);
        }
    }

    public MysqlBackupTaskPayload parseBackupTaskPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("Backup task parametersJson is required.");
        }

        try {
            return objectMapper.readValue(payloadJson, MysqlBackupTaskPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid backup task parametersJson.", exception);
        }
    }

    public MysqlBackupTaskResultPayload parseBackupTaskResultPayload(String resultPayloadJson) {
        if (resultPayloadJson == null || resultPayloadJson.isBlank()) {
            throw new IllegalArgumentException("Backup task resultPayloadJson is required.");
        }

        try {
            return objectMapper.readValue(resultPayloadJson, MysqlBackupTaskResultPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid backup task resultPayloadJson.", exception);
        }
    }

    private void validateBackupResultPayload(MysqlBackupTaskResultPayload resultPayload) {
        if (resultPayload.backupFile() == null || resultPayload.backupFile().isBlank()) {
            throw new IllegalArgumentException(
                    "backupFile is required in backup task result payload.");
        }

        if (resultPayload.status() == null
                || !"VERIFIED".equalsIgnoreCase(resultPayload.status())) {
            throw new IllegalStateException(
                    "Backup artifact must be VERIFIED before restore verification. status="
                            + resultPayload.status());
        }
    }

    private String buildTemporaryDatabaseName(String databaseName, Long operationJobId) {
        String timestamp =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        return "restore_verify_" + sanitizeDatabaseName(databaseName) + "_" + operationJobId + "_"
                + timestamp;
    }

    private String sanitizeDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            return "database";
        }

        StringBuilder builder = new StringBuilder();

        for (char character : databaseName.toCharArray()) {
            if (character >= 'a' && character <= 'z') {
                builder.append(character);
                continue;
            }

            if (character >= 'A' && character <= 'Z') {
                builder.append(character);
                continue;
            }

            if (character >= '0' && character <= '9') {
                builder.append(character);
                continue;
            }

            if (character == '_') {
                builder.append(character);
                continue;
            }

            builder.append('_');
        }

        String result = builder.toString();

        if (result.isBlank()) {
            return "database";
        }

        return result;
    }
}
