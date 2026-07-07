package com.dbfleetops.database.infra;

import com.dbfleetops.database.application.DatabaseConfigurationReaderPort;
import com.dbfleetops.database.domain.DatabaseCredential;
import com.dbfleetops.database.domain.ManagedDatabase;
import com.dbfleetops.database.dto.DatabaseConfigurationItem;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

@Component
public class MySqlConfigurationReaderAdapter implements DatabaseConfigurationReaderPort {

    private final ManagedDatabaseRepository managedDatabaseRepository;
    private final DatabaseCredentialRepository databaseCredentialRepository;

    public MySqlConfigurationReaderAdapter(ManagedDatabaseRepository managedDatabaseRepository,
            DatabaseCredentialRepository databaseCredentialRepository) {
        this.managedDatabaseRepository = managedDatabaseRepository;
        this.databaseCredentialRepository = databaseCredentialRepository;
    }

    @Override
    public ConfigurationEngineType supports() {
        return ConfigurationEngineType.MYSQL;
    }

    @Override
    public List<DatabaseConfigurationItem> collectConfiguration(Long databaseId) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        ManagedDatabase database = managedDatabaseRepository.findById(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Managed database not found. databaseId=" + databaseId));

        DatabaseCredential credential = databaseCredentialRepository.findByDatabaseId(databaseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Database credential not found. databaseId=" + databaseId));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(createDataSource(database, credential));

        return jdbcTemplate.query("SHOW GLOBAL VARIABLES",
                (resultSet, rowNum) -> new DatabaseConfigurationItem(
                        resultSet.getString("Variable_name"), resultSet.getString("Value"), null,
                        null, null, "GLOBAL"));
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
