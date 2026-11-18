package com.dbfleetops.backup.application;

import com.dbfleetops.backup.dto.MysqlBackupTaskPayload;
import com.dbfleetops.backup.dto.MysqlBackupTaskResultPayload;
import com.dbfleetops.backup.dto.MysqlRestoreVerifyTaskPayload;
import com.dbfleetops.backup.dto.BusinessRulePayload;
import com.dbfleetops.backup.domain.BusinessVerificationProfile;
import com.dbfleetops.backup.domain.BusinessVerificationProfiles;
import com.dbfleetops.backup.infra.CodeBusinessVerificationProfiles;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashSet;

@Component
public class RestoreVerifyTaskPayloadFactory {

    private final ObjectMapper objectMapper;
    private final BusinessVerificationProfiles profiles;

    @Autowired
    public RestoreVerifyTaskPayloadFactory(ObjectMapper objectMapper,
            BusinessVerificationProfiles profiles) {
        this.objectMapper = objectMapper;
        this.profiles = profiles;
    }

    public RestoreVerifyTaskPayloadFactory(ObjectMapper objectMapper) {
        this(objectMapper, new CodeBusinessVerificationProfiles());
    }

    public String createRestoreVerifyTaskPayloadJson(Long restoreVerifyTaskOperationJobId,
            Long backupTaskId, String backupTaskParametersJson,
            String backupTaskResultPayloadJson) {
        MysqlBackupTaskPayload backupTaskPayload = parseBackupTaskPayload(backupTaskParametersJson);

        MysqlBackupTaskResultPayload backupResultPayload =
                parseBackupTaskResultPayload(backupTaskResultPayloadJson);

        validateBackupResultPayload(backupResultPayload);

        BusinessVerificationProfile profile = findProfile(
                backupTaskPayload.businessVerificationProfileKey());

        MysqlRestoreVerifyTaskPayload restoreVerifyTaskPayload = new MysqlRestoreVerifyTaskPayload(
                restoreVerifyTaskOperationJobId, backupTaskPayload.databaseId(), backupTaskId,
                backupTaskPayload.databaseName(), backupResultPayload.backupFile(),
                backupResultPayload.checksumSha256(),
                backupTaskPayload.host(), backupTaskPayload.port(), null, null,
                buildTemporaryDatabaseName(backupTaskPayload.databaseName(),
                        restoreVerifyTaskOperationJobId),
                expectedTables(backupTaskPayload.expectedTables(), profile),
                backupTaskPayload.shouldVerifyRowCount(), backupTaskPayload.shouldCleanup(),
                profile == null ? null : profile.key(), rules(profile));

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

    private BusinessVerificationProfile findProfile(String profileKey) {
        if (profileKey == null || profileKey.isBlank()) {
            return null;
        }
        return profiles.find(profileKey).orElseThrow(() -> new IllegalArgumentException(
                "업무 검증 Profile을 찾을 수 없습니다. profileKey=" + profileKey));
    }

    private List<String> expectedTables(List<String> expectedTables,
            BusinessVerificationProfile profile) {
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        if (expectedTables != null) {
            tables.addAll(expectedTables);
        }
        if (profile != null) {
            tables.addAll(profile.requiredTables());
        }
        return List.copyOf(tables);
    }

    private List<BusinessRulePayload> rules(BusinessVerificationProfile profile) {
        if (profile == null) {
            return List.of();
        }
        return profile.rules().stream().map(rule -> new BusinessRulePayload(rule.code(),
                rule.name(), rule.query(), rule.timeoutMillis())).toList();
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
