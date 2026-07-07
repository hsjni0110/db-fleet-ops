package com.dbfleetops.database.application;

import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ParameterValueType;

public interface DatabaseConfigurationApplyPort {

    ConfigurationEngineType supports();

    ConfigurationApplyCommandResult applyGlobalParameter(Long databaseId, String parameterName,
            String targetValue, ParameterValueType valueType);
}
