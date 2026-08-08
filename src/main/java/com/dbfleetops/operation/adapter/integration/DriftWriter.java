package com.dbfleetops.operation.adapter.integration;
import com.dbfleetops.policy.dto.*;
/** 원하는 설정과 실제 설정의 차이를 저장할 때 사용하는 출구입니다. */
public interface DriftWriter {
    /** 설정 비교 결과를 Drift 기록으로 저장하고 조회용 응답을 반환합니다. */
    ConfigurationDriftResponse save(ConfigurationComparisonResult result);
}
