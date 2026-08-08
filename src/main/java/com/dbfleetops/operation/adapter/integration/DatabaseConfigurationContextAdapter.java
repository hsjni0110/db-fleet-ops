package com.dbfleetops.operation.adapter.integration;
import com.dbfleetops.database.application.*;
import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.policy.domain.*;
import org.springframework.stereotype.Component;
@Component
public class DatabaseConfigurationContextAdapter implements ConfigurationCommand {
    private final DatabaseConfigurationApplyPortRegistry registry;
    public DatabaseConfigurationContextAdapter(DatabaseConfigurationApplyPortRegistry registry) {
        this.registry = registry;
    }
    public ConfigurationApplyCommandResult apply(ConfigurationEngineType engine, Long databaseId,
            String name, String value, ParameterValueType type) {
        return registry.getApplyPort(engine).applyGlobalParameter(databaseId, name, value, type);
    }
}
