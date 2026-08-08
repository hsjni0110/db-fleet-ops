package com.dbfleetops.operation.application.required;

import java.util.Optional;

/** Operation이 작업 대상 Database의 상태와 배정 Agent를 확인할 때 사용하는 출구입니다. */
public interface DatabaseReader {
    /** Database ID로 관리 대상을 조회하며, 존재하지 않으면 빈 값을 반환합니다. */
    Optional<DatabaseExecutionTarget> findDatabase(Long databaseId);
}
