package com.dbfleetops.database.application;

import com.dbfleetops.policy.domain.ConfigurationEngineType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DatabaseConfigurationApplyPortRegistry {

    private final Map<ConfigurationEngineType, DatabaseConfigurationApplyPort> applyPorts;

    public DatabaseConfigurationApplyPortRegistry(List<DatabaseConfigurationApplyPort> applyPorts) {
        this.applyPorts = applyPorts.stream().collect(
                Collectors.toMap(DatabaseConfigurationApplyPort::supports, Function.identity()));
    }

    public DatabaseConfigurationApplyPort getApplyPort(ConfigurationEngineType engineType) {
        if (engineType == null) {
            throw new IllegalArgumentException("engineType is required.");
        }

        DatabaseConfigurationApplyPort applyPort = applyPorts.get(engineType);

        if (applyPort == null) {
            throw new IllegalArgumentException(
                    "Unsupported database engine for configuration apply. engineType="
                            + engineType);
        }

        return applyPort;
    }
}
