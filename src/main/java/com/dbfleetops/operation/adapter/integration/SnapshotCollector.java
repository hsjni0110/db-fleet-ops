package com.dbfleetops.operation.adapter.integration;
import com.dbfleetops.policy.domain.*;
/** Operation이 Database의 현재 설정값을 수집할 때 사용하는 출구입니다. */
public interface SnapshotCollector {
    /** DBMS 종류에 맞게 현재 설정을 읽고 새로운 Snapshot으로 저장합니다. */
    ConfigurationSnapshot collect(Long databaseId, ConfigurationEngineType engineType);
}
