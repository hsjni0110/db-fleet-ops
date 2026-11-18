package com.dbfleetops.backup.dto;

import com.dbfleetops.backup.domain.BusinessVerificationProfile;
import com.dbfleetops.database.domain.DatabaseEngine;

import java.util.List;

public record BusinessVerificationProfileResponse(String key, String name, String description,
        DatabaseEngine engine, List<String> requiredTables, int ruleCount) {
    public static BusinessVerificationProfileResponse from(BusinessVerificationProfile profile) {
        return new BusinessVerificationProfileResponse(profile.key(), profile.name(),
                profile.description(), profile.engine(), profile.requiredTables(),
                profile.rules().size());
    }
}
