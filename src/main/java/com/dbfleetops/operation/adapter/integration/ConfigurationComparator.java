package com.dbfleetops.operation.adapter.integration;
import com.dbfleetops.policy.dto.ConfigurationComparisonResult;
/** 원하는 설정과 현재 Database 설정을 비교할 때 사용하는 출구입니다. */
public interface ConfigurationComparator {
    /** 설정 Profile과 실제 Snapshot을 비교해 다른 항목과 같은 항목을 정리합니다. */
    ConfigurationComparisonResult compare(Long profileId, Long snapshotId);
}
