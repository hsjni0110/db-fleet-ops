package com.dbfleetops.policy.dto;

import com.dbfleetops.policy.domain.ComplianceStatus;
import com.dbfleetops.policy.domain.ConfigurationEngineType;

import java.util.List;

public record ConfigurationComparisonResult(Long profileId, Long snapshotId, Long databaseId,
        ConfigurationEngineType engineType, ComplianceStatus overallStatus, int totalCount,
        int compliantCount, int nonCompliantCount, int missingCount,
        List<ConfigurationComparisonItem> items) {

    public static ConfigurationComparisonResult of(Long profileId, Long snapshotId, Long databaseId,
            ConfigurationEngineType engineType, List<ConfigurationComparisonItem> items) {
        int compliantCount = countByStatus(items, ComplianceStatus.COMPLIANT);

        int nonCompliantCount = countByStatus(items, ComplianceStatus.NON_COMPLIANT);

        int missingCount = countByStatus(items, ComplianceStatus.MISSING);

        ComplianceStatus overallStatus =
                nonCompliantCount == 0 && missingCount == 0 ? ComplianceStatus.COMPLIANT
                        : ComplianceStatus.NON_COMPLIANT;

        return new ConfigurationComparisonResult(profileId, snapshotId, databaseId, engineType,
                overallStatus, items.size(), compliantCount, nonCompliantCount, missingCount,
                items);
    }

    private static int countByStatus(List<ConfigurationComparisonItem> items,
            ComplianceStatus status) {
        return (int) items.stream().filter(item -> item.complianceStatus() == status).count();
    }
}
