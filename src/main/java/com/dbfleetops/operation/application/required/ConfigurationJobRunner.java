package com.dbfleetops.operation.application.required;

import com.dbfleetops.operation.domain.OperationJob;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;

/** Operation이 Policy 영역에 설정 작업 실행을 요청하는 출구입니다. */
public interface ConfigurationJobRunner {
    void validateApply(Long databaseId, CreateConfigurationApplyJobRequest request);
    ConfigurationCheckOutcome check(OperationJob job);
    ConfigurationApplyOutcome apply(OperationJob job);
}
