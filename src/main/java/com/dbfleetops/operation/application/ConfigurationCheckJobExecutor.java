package com.dbfleetops.operation.application;

import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.infra.ManagedDatabaseRepository;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ConfigurationCheckJobPayload;
import com.dbfleetops.policy.application.ConfigurationComparisonService;
import com.dbfleetops.policy.application.ConfigurationDriftService;
import com.dbfleetops.policy.application.ConfigurationSnapshotService;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.dto.ConfigurationComparisonResult;
import com.dbfleetops.policy.dto.ConfigurationDriftResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationCheckJobExecutor {

    private final ManagedDatabaseRepository databaseRepository;
    private final ConfigurationSnapshotService snapshotService;
    private final ConfigurationComparisonService comparisonService;
    private final ConfigurationDriftService driftService;
    private final ObjectMapper objectMapper;

    public ConfigurationCheckJobExecutor(ManagedDatabaseRepository databaseRepository,
            ConfigurationSnapshotService snapshotService,
            ConfigurationComparisonService comparisonService,
            ConfigurationDriftService driftService, ObjectMapper objectMapper) {
        this.databaseRepository = databaseRepository;
        this.snapshotService = snapshotService;
        this.comparisonService = comparisonService;
        this.driftService = driftService;
        this.objectMapper = objectMapper;
    }

    public ConfigurationDriftResponse execute(OperationJob job) {
        if (job == null) {
            throw new IllegalArgumentException("job is required.");
        }

        ConfigurationCheckJobPayload payload = parsePayload(job.getRequestPayload());

        if (payload.profileId() == null) {
            throw new IllegalArgumentException("profileId is required.");
        }

        ManagedDatabase database = databaseRepository.findById(job.getTargetDatabaseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Database not found. databaseId=" + job.getTargetDatabaseId()));

        ConfigurationEngineType engineType = toConfigurationEngineType(database);

        ConfigurationSnapshot snapshot =
                snapshotService.collectSnapshot(database.getId(), engineType);

        ConfigurationComparisonResult comparisonResult =
                comparisonService.compare(payload.profileId(), snapshot.getId());

        return driftService.saveDrift(comparisonResult);
    }

    private ConfigurationCheckJobPayload parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("Configuration check job payload is required.");
        }

        try {
            return objectMapper.readValue(payloadJson, ConfigurationCheckJobPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid configuration check job payload.",
                    exception);
        }
    }

    private ConfigurationEngineType toConfigurationEngineType(ManagedDatabase database) {
        if (database.getEngine() == null) {
            throw new IllegalArgumentException(
                    "Database engineType is required. databaseId=" + database.getId());
        }

        return ConfigurationEngineType.valueOf(database.getEngine().name());
    }
}
