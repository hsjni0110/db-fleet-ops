package com.dbfleetops.operation.adapter.integration;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.policy.dto.ConfigurationApplyValidationResult;
/** 요청한 설정 변경이 안전하고 허용된 작업인지 확인할 때 사용하는 출구입니다. */
public interface ConfigurationValidator {
    /** Database와 변경 요청을 검사하고 항목별 적용 가능 여부를 반환합니다. */
    ConfigurationApplyValidationResult validate(Long databaseId, CreateConfigurationApplyJobRequest request);
}
