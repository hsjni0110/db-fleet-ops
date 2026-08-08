package com.dbfleetops.operation.job.application.required;

/**
 * 설정 변경 Job이 요청값을 검사하고 DB에 적용할 때 사용하는 기능입니다.
 * Operation은 반환된 결과로 Job의 성공과 실패만 결정합니다.
 */
public interface ConfigurationChange {

    /** Job을 만들기 전에 요청한 설정값을 해당 DB에 적용할 수 있는지 확인합니다. */
    void validate(ConfigurationChangeCommand command);

    /** 검증된 설정값을 DB에 적용하고 항목별 처리 결과를 반환합니다. */
    ConfigurationApplyOutcome apply(ConfigurationChangeCommand command);
}
