package com.dbfleetops.database.application;

import com.dbfleetops.database.dto.DatabaseConfigurationItem;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConfigurationReaderPortRegistryTest {

    @Test
    void getReaderReturnsMatchingReader() {
        DatabaseConfigurationReaderPort mysqlReader =
                new FakeConfigurationReaderPort(ConfigurationEngineType.MYSQL);

        DatabaseConfigurationReaderPortRegistry registry =
                new DatabaseConfigurationReaderPortRegistry(List.of(mysqlReader));

        DatabaseConfigurationReaderPort reader = registry.getReader(ConfigurationEngineType.MYSQL);

        assertThat(reader.supports()).isEqualTo(ConfigurationEngineType.MYSQL);
    }

    @Test
    void getReaderThrowsExceptionWhenUnsupportedEngine() {
        DatabaseConfigurationReaderPort mysqlReader =
                new FakeConfigurationReaderPort(ConfigurationEngineType.MYSQL);

        DatabaseConfigurationReaderPortRegistry registry =
                new DatabaseConfigurationReaderPortRegistry(List.of(mysqlReader));

        assertThatThrownBy(() -> registry.getReader(ConfigurationEngineType.POSTGRESQL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported database engine");
    }

    private static class FakeConfigurationReaderPort implements DatabaseConfigurationReaderPort {

        private final ConfigurationEngineType engineType;

        private FakeConfigurationReaderPort(ConfigurationEngineType engineType) {
            this.engineType = engineType;
        }

        @Override
        public ConfigurationEngineType supports() {
            return engineType;
        }

        @Override
        public List<DatabaseConfigurationItem> collectConfiguration(Long databaseId) {
            return List.of();
        }
    }
}
