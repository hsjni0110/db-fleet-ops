package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.operation.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.application.required.DatabaseExecutionTarget;
import com.dbfleetops.operation.application.required.DatabaseReader;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.ConfigurationCheckJobPayload;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.dto.ConfigurationComparisonResult;
import com.dbfleetops.policy.dto.ConfigurationDriftResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationCheckJobExecutor {

    private final DatabaseReader databaseRepository;
    private final SnapshotCollector snapshotService;
    private final ConfigurationComparator comparisonService;
    private final DriftWriter driftService;
    private final ObjectMapper objectMapper;

    public ConfigurationCheckJobExecutor(DatabaseReader databaseRepository,
            SnapshotCollector snapshotService,
            ConfigurationComparator comparisonService,
            DriftWriter driftService, ObjectMapper objectMapper) {
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

        DatabaseExecutionTarget database = databaseRepository.findDatabase(job.getTargetDatabaseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Database not found. databaseId=" + job.getTargetDatabaseId()));

        ConfigurationEngineType engineType = toConfigurationEngineType(database);

        ConfigurationSnapshot snapshot =
                snapshotService.collect(database.id(), engineType);

        ConfigurationComparisonResult comparisonResult =
                comparisonService.compare(payload.profileId(), snapshot.getId());

        return driftService.save(comparisonResult);
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

    private ConfigurationEngineType toConfigurationEngineType(DatabaseExecutionTarget database) {
        if (database.engine() == null) {
            throw new IllegalArgumentException(
                        "Database engineType is required. databaseId=" + database.id());
        }

        return ConfigurationEngineType.valueOf(database.engine());
    }
}
