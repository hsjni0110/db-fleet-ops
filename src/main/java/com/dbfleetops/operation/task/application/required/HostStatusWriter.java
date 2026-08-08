package com.dbfleetops.operation.task.application.required;

/** Agent가 수집한 Host 상태 결과를 담당 영역에 전달하는 출구입니다. */
public interface HostStatusWriter {
    /** 결과 JSON을 검사하고 CPU, Memory, Disk 측정값을 저장합니다. */
    void record(Long agentId, String resultPayloadJson);
}
