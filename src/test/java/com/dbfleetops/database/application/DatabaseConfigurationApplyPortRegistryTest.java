package com.dbfleetops.database.application;

import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ParameterValueType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConfigurationApplyPortRegistryTest {

    @Test
    void getApplyPortReturnsMatchedPort() {
        DatabaseConfigurationApplyPort mysqlPort =
                new FakeDatabaseConfigurationApplyPort(ConfigurationEngineType.MYSQL);

        DatabaseConfigurationApplyPortRegistry registry =
                new DatabaseConfigurationApplyPortRegistry(List.of(mysqlPort));

        DatabaseConfigurationApplyPort result =
                registry.getApplyPort(ConfigurationEngineType.MYSQL);

        assertThat(result).isSameAs(mysqlPort);
    }

    @Test
    void getApplyPortThrowsExceptionWhenEngineTypeIsUnsupported() {
        DatabaseConfigurationApplyPortRegistry registry =
                new DatabaseConfigurationApplyPortRegistry(List.of());

        assertThatThrownBy(() -> registry.getApplyPort(ConfigurationEngineType.POSTGRESQL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported database engine");
    }

    private static class FakeDatabaseConfigurationApplyPort
            implements DatabaseConfigurationApplyPort {

        private final ConfigurationEngineType engineType;

        private FakeDatabaseConfigurationApplyPort(ConfigurationEngineType engineType) {
            this.engineType = engineType;
        }

        @Override
        public ConfigurationEngineType supports() {
            return engineType;
        }

        @Override
        public ConfigurationApplyCommandResult applyGlobalParameter(Long databaseId,
                String parameterName, String targetValue, ParameterValueType valueType) {
            return ConfigurationApplyCommandResult.success(parameterName, targetValue,
                    "fake success");
        }
    }
}
