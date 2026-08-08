package com.dbfleetops.operation.adapter.integration;
import com.dbfleetops.policy.domain.ParameterValueType;
/** 설정 적용 후 실제 값이 요청한 값과 같은지 확인할 때 사용하는 출구입니다. */
public interface ConfigurationValueMatcher {
    /** 문자열, 숫자, Boolean 등 설정값 종류에 맞는 규칙으로 두 값을 비교합니다. */
    boolean matches(String expected, String actual, ParameterValueType type);
}
