package com.dbfleetops.operation.adapter.integration;

import com.dbfleetops.operation.application.required.*;
import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.policy.domain.ConfigurationApplyStatus;
import org.springframework.stereotype.Component;

/** Operation의 설정 작업 요청을 Policy 실행 기능에 연결합니다. */
@Component
public class ConfigurationJobAdapter implements ConfigurationJobRunner {
    private final ConfigurationValidator validator;
    private final ConfigurationCheckJobExecutor checks;
    private final ConfigurationApplyJobExecutor applies;
    public ConfigurationJobAdapter(ConfigurationValidator validator,
            ConfigurationCheckJobExecutor checks, ConfigurationApplyJobExecutor applies) {
        this.validator = validator; this.checks = checks; this.applies = applies;
    }
    public void validateApply(Long databaseId, CreateConfigurationApplyJobRequest request) {
        validator.validate(databaseId, request);
    }
    public ConfigurationCheckOutcome check(OperationJob job) {
        var result = checks.execute(job);
        return new ConfigurationCheckOutcome(result.driftId(), result.status().name());
    }
    public ConfigurationApplyOutcome apply(OperationJob job) {
        var result = applies.execute(job);
        return new ConfigurationApplyOutcome(result.getId(),
                result.getStatus() == ConfigurationApplyStatus.SUCCEEDED,
                result.getStatus().name(), result.getSuccessCount(), result.getFailedCount(),
                result.getSkippedCount());
    }
}
