package com.dbfleetops.database.application;

import com.dbfleetops.policy.domain.ConfigurationEngineType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DatabaseConfigurationReaderPortRegistry {

    private final Map<ConfigurationEngineType, DatabaseConfigurationReaderPort> readers;

    public DatabaseConfigurationReaderPortRegistry(List<DatabaseConfigurationReaderPort> readers) {
        this.readers = readers.stream().collect(
                Collectors.toMap(DatabaseConfigurationReaderPort::supports, Function.identity()));
    }

    public DatabaseConfigurationReaderPort getReader(ConfigurationEngineType engineType) {
        if (engineType == null) {
            throw new IllegalArgumentException("engineType is required.");
        }

        DatabaseConfigurationReaderPort reader = readers.get(engineType);

        if (reader == null) {
            throw new IllegalArgumentException(
                    "Unsupported database engine for configuration collection. engineType="
                            + engineType);
        }

        return reader;
    }
}
