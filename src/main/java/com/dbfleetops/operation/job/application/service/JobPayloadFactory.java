package com.dbfleetops.operation.job.application.service;

import com.dbfleetops.operation.job.dto.ConfigurationApplyJobParameterPayload;
import com.dbfleetops.operation.job.dto.ConfigurationApplyJobPayload;
import com.dbfleetops.operation.job.dto.ConfigurationCheckJobPayload;
import com.dbfleetops.operation.job.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.job.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.operation.job.dto.CreateConfigurationCheckJobRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JobPayloadFactory {

    private final ObjectMapper objectMapper;

    public JobPayloadFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String backup(CreateBackupJobRequest request) {
        return write(new BackupJobPayload(valueOrEmpty(request.reason()), request.requestedBy()));
    }

    public String configurationCheck(CreateConfigurationCheckJobRequest request) {
        return write(new ConfigurationCheckJobPayload(
                request.profileId(), valueOrEmpty(request.reason()), request.requestedBy()));
    }

    public String configurationApply(CreateConfigurationApplyJobRequest request) {
        var parameters = request.parameters().stream()
                .map(parameter -> new ConfigurationApplyJobParameterPayload(
                        parameter.parameterName(), parameter.targetValue()))
                .toList();

        return write(new ConfigurationApplyJobPayload(
                request.profileId(), valueOrEmpty(request.reason()), request.requestedBy(),
                parameters));
    }

    private String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Job 요청 내용을 JSON으로 만들 수 없습니다.", exception);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record BackupJobPayload(String reason, String requestedBy) {
    }
}
