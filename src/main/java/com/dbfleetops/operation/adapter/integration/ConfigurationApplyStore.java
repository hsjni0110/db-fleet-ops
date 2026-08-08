package com.dbfleetops.operation.adapter.integration;
import com.dbfleetops.policy.domain.*;
import java.util.List;
/** 설정 적용 과정과 적용 전후 측정값을 저장하거나 조회할 때 사용하는 출구입니다. */
public interface ConfigurationApplyStore {
    /** 설정 적용 전체 진행 상태를 저장합니다. */
    ConfigurationApply saveApply(ConfigurationApply apply);

    /** 설정 항목별 적용 결과를 한 번에 저장합니다. */
    List<ConfigurationApplyItem> saveItems(List<ConfigurationApplyItem> items);

    /** 지정한 Snapshot에 기록된 설정값 목록을 이름순으로 조회합니다. */
    List<ConfigurationSnapshotItem> findSnapshotItems(Long snapshotId);
}
