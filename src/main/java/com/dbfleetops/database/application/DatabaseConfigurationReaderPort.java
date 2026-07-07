package com.dbfleetops.database.application;

import com.dbfleetops.database.dto.DatabaseConfigurationItem;
import com.dbfleetops.policy.domain.ConfigurationEngineType;

import java.util.List;

public interface DatabaseConfigurationReaderPort {

    ConfigurationEngineType supports();

    List<DatabaseConfigurationItem> collectConfiguration(Long databaseId);
}
