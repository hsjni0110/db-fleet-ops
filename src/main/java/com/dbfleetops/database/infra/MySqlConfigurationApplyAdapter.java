package com.dbfleetops.database.infra;

import com.dbfleetops.database.application.DatabaseConfigurationApplyPort;
import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.ConfigurationApplyCommandResult;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ParameterValueType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class MySqlConfigurationApplyAdapter implements DatabaseConfigurationApplyPort {

    private static final Set<String> ALLOWED_PARAMETERS =
            Set.of("slow_query_log", "long_query_time", "max_connections");

    private static final List<String> TRUE_VALUES = List.of("ON", "TRUE", "1", "YES", "Y");

    private static final List<String> FALSE_VALUES = List.of("OFF", "FALSE", "0", "NO", "N");

    private final ManagedDatabaseRepository managedDatabaseRepository;
    private final DatabaseCredentialRepository databaseCredentialRepository;

    public MySqlConfigurationApplyAdapter(ManagedDatabaseRepository managedDatabaseRepository,
            DatabaseCredentialRepository databaseCredentialRepository) {
        this.managedDatabaseRepository = managedDatabaseRepository;
        this.databaseCredentialRepository = databaseCredentialRepository;
    }

    @Override
    public ConfigurationEngineType supports() {
        return ConfigurationEngineType.MYSQL;
    }

    @Override
    public ConfigurationApplyCommandResult applyGlobalParameter(Long databaseId,
            String parameterName, String targetValue, ParameterValueType valueType) {
        validateInput(databaseId, parameterName, targetValue, valueType);

        String normalizedParameterName = normalizeParameterName(parameterName);

        if (!ALLOWED_PARAMETERS.contains(normalizedParameterName)) {
            throw new IllegalArgumentException(
                    "Parameter is not allowed for MySQL SET GLOBAL. parameterName="
                            + parameterName);
        }

        ManagedDatabase database = managedDatabaseRepository.findById(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Managed database not found. databaseId=" + databaseId));

        DatabaseCredential credential = databaseCredentialRepository.findByDatabaseId(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Database credential not found. databaseId=" + databaseId));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(createDataSource(database, credential));

        String normalizedValue = normalizeValue(targetValue, valueType);

        String sql = buildSetGlobalSql(normalizedParameterName, normalizedValue, valueType);

        jdbcTemplate.execute(sql);

        return ConfigurationApplyCommandResult.success(normalizedParameterName, normalizedValue,
                "MySQL global parameter applied.");
    }

    private void validateInput(Long databaseId, String parameterName, String targetValue,
            ParameterValueType valueType) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        if (parameterName == null || parameterName.isBlank()) {
            throw new IllegalArgumentException("parameterName is required.");
        }

        if (targetValue == null || targetValue.isBlank()) {
            throw new IllegalArgumentException("targetValue is required.");
        }

        if (valueType == null) {
            throw new IllegalArgumentException("valueType is required.");
        }
    }

    private String normalizeParameterName(String parameterName) {
        return parameterName.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeValue(String targetValue, ParameterValueType valueType) {
        return switch (valueType) {
            case BOOLEAN -> normalizeBooleanValue(targetValue);
            case NUMBER -> normalizeNumberValue(targetValue);
            case STRING -> normalizeStringValue(targetValue);
        };
    }

    private String normalizeBooleanValue(String targetValue) {
        String normalized = targetValue.trim().toUpperCase(Locale.ROOT);

        if (TRUE_VALUES.contains(normalized)) {
            return "ON";
        }

        if (FALSE_VALUES.contains(normalized)) {
            return "OFF";
        }

        throw new IllegalArgumentException(
                "Invalid BOOLEAN targetValue. targetValue=" + targetValue);
    }

    private String normalizeNumberValue(String targetValue) {
        try {
            return new BigDecimal(targetValue.trim()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid NUMBER targetValue. targetValue=" + targetValue);
        }
    }

    private String normalizeStringValue(String targetValue) {
        String value = targetValue.trim();

        if (value.contains(";") || value.contains("--") || value.contains("/*")
                || value.contains("*/")) {
            throw new IllegalArgumentException("Unsafe STRING targetValue.");
        }

        return value;
    }

    private String buildSetGlobalSql(String parameterName, String normalizedValue,
            ParameterValueType valueType) {
        return switch (valueType) {
            case BOOLEAN, STRING -> "SET GLOBAL " + parameterName + " = '"
                    + escapeSqlLiteral(normalizedValue) + "'";

            case NUMBER -> "SET GLOBAL " + parameterName + " = " + normalizedValue;
        };
    }

    private String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private DataSource createDataSource(ManagedDatabase database, DatabaseCredential credential) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();

        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");

        dataSource.setUrl(buildJdbcUrl(database));

        dataSource.setUsername(credential.getUsername());

        dataSource.setPassword(credential.getPassword());

        return dataSource;
    }

    private String buildJdbcUrl(ManagedDatabase database) {
        return "jdbc:mysql://" + database.getHost() + ":" + database.getPort() + "/"
                + database.getDatabaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }
}
