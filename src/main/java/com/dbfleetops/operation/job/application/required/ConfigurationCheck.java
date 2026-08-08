package com.dbfleetops.operation.job.application.required;

/**
 * 설정 점검 Job이 현재 DB 설정을 설정 기준과 비교할 때 사용하는 기능입니다.
 * 실제 설정 수집과 비교·저장은 Adapter가 설정 영역에 연결합니다.
 */
public interface ConfigurationCheck {

    /** 지정한 DB의 현재 설정을 읽고 설정 기준과 비교한 결과를 반환합니다. */
    ConfigurationCheckOutcome check(ConfigurationCheckCommand command);
}
