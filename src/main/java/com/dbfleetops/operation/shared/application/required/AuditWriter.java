package com.dbfleetops.operation.shared.application.required;

/** Operation에서 일어난 중요한 행동을 감사 기록으로 남길 때 사용하는 출구입니다. */
public interface AuditWriter {
    /** 누가 어떤 대상에 무슨 행동을 했고 결과가 어땠는지 기록합니다. */
    void record(String actor, String action, String targetType, String targetId,
            String result, String message);
}
