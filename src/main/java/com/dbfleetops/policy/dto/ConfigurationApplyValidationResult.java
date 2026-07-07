package com.dbfleetops.policy.dto;

import java.util.List;

public record ConfigurationApplyValidationResult(Long databaseId, Long profileId,
        List<ConfigurationApplyValidationItem> items) {

    public int totalCount() {
        return items == null ? 0 : items.size();
    }
}
