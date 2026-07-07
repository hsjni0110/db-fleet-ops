package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationProfile;
import com.dbfleetops.policy.domain.ConfigurationProfileStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ConfigurationProfileResponse(Long profileId, String profileName,
        ConfigurationEngineType engineType, String environment, String versionRange,
        String description, ConfigurationProfileStatus status, LocalDateTime createdAt,
        LocalDateTime updatedAt, List<ConfigurationProfileParameterResponse> parameters) {

    public static ConfigurationProfileResponse from(ConfigurationProfile profile,
            List<ConfigurationProfileParameterResponse> parameters) {
        return new ConfigurationProfileResponse(profile.getId(), profile.getProfileName(),
                profile.getEngineType(), profile.getEnvironment(), profile.getVersionRange(),
                profile.getDescription(), profile.getStatus(), profile.getCreatedAt(),
                profile.getUpdatedAt(), parameters);
    }

    public static ConfigurationProfileResponse from(ConfigurationProfile profile) {
        return from(profile, List.of());
    }
}
