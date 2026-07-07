package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ConfigurationEngineType;

public record CreateConfigurationProfileRequest(String profileName,
        ConfigurationEngineType engineType, String environment, String versionRange,
        String description) {
}
