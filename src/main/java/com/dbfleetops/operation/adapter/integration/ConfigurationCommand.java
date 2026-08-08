package com.dbfleetops.operation.adapter.integration;
import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.policy.domain.*;
/** Operation이 실제 Database의 설정값 하나를 변경할 때 사용하는 출구입니다. */
public interface ConfigurationCommand {
    /** DBMS 종류에 맞는 실행 방법으로 설정값 하나를 적용하고 실행 결과를 반환합니다. */
    ConfigurationApplyCommandResult apply(ConfigurationEngineType engine, Long databaseId,
            String parameterName, String value, ParameterValueType valueType);
}
