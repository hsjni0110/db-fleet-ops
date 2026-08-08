package com.dbfleetops.operation.job.application.service;

import com.dbfleetops.operation.job.application.required.ConfigurationChangeCommand;
import com.dbfleetops.operation.job.application.required.ConfigurationChangeItem;
import com.dbfleetops.operation.job.application.required.ConfigurationCheckCommand;
import com.dbfleetops.operation.job.domain.OperationJob;
import com.dbfleetops.operation.job.dto.ConfigurationApplyJobPayload;
import com.dbfleetops.operation.job.dto.ConfigurationCheckJobPayload;
import com.dbfleetops.operation.job.dto.CreateConfigurationApplyJobRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Job 요청과 저장된 Payload를 설정 점검·변경 명령으로 바꿉니다. */
@Component
public class ConfigurationCommandFactory {

    private final ObjectMapper objectMapper;

    public ConfigurationCommandFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ConfigurationChangeCommand change(Long databaseId,
            CreateConfigurationApplyJobRequest request) {
        return new ConfigurationChangeCommand(null, databaseId, request.profileId(),
                request.requestedBy(), request.reason(), request.parameters().stream()
                        .map(item -> new ConfigurationChangeItem(
                                item.parameterName(), item.targetValue()))
                        .toList());
    }

    public ConfigurationCheckCommand check(OperationJob job) {
        ConfigurationCheckJobPayload payload = read(
                job.getRequestPayload(), ConfigurationCheckJobPayload.class,
                "설정 점검 Job Payload가 올바르지 않습니다.");

        return new ConfigurationCheckCommand(job.getId(), job.getTargetDatabaseId(),
                payload.profileId(), payload.requestedBy());
    }

    public ConfigurationChangeCommand change(OperationJob job) {
        ConfigurationApplyJobPayload payload = read(
                job.getRequestPayload(), ConfigurationApplyJobPayload.class,
                "설정 변경 Job Payload가 올바르지 않습니다.");

        return new ConfigurationChangeCommand(job.getId(), job.getTargetDatabaseId(),
                payload.profileId(), payload.requestedBy(), payload.reason(),
                payload.parameters().stream()
                        .map(item -> new ConfigurationChangeItem(
                                item.parameterName(), item.targetValue()))
                        .toList());
    }

    private <T> T read(String json, Class<T> type, String errorMessage) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }

        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(errorMessage, exception);
        }
    }
}
