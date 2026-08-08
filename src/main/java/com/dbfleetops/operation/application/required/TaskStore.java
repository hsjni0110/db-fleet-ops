package com.dbfleetops.operation.application.required;

import com.dbfleetops.operation.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Operation이 Task를 저장하고 조회할 때 사용하는 출구입니다.
 * 실제 DB 접근 방식은 Adapter가 담당하며, 이 인터페이스에는 JPA 같은 기술을 드러내지 않습니다.
 */
public interface TaskStore {
    /** 새 Task 또는 상태가 바뀐 Task를 저장합니다. */
    OperationTask save(OperationTask task);

    /** Task ID로 Task를 잠가 조회하며, 존재하지 않으면 빈 값을 반환합니다. */
    Optional<OperationTask> findById(Long id);

    /** Agent에게 배정된 가장 오래된 대기 Task 하나를 잠가 조회합니다. */
    Optional<OperationTask> findNextForUpdate(Long agentId, OperationTaskStatus status);

    /** Lease가 만료된 Task를 제한된 개수만 잠가서 조회합니다. */
    List<OperationTask> findExpiredForUpdate(OperationTaskStatus status, LocalDateTime now, int limit);

    /** Agent가 최근에 실행한 Task 목록을 조회합니다. */
    List<OperationTask> findRecentByAgent(Long agentId);

    /** 하나의 Job에 연결된 Task를 생성 순서대로 조회합니다. */
    List<OperationTask> findByJob(Long jobId);

    /** 같은 Job에 같은 종류의 Task가 이미 있는지 확인합니다. */
    boolean existsByJobAndType(Long jobId, OperationTaskType taskType);

    /** 지정한 상태에 해당하는 Task 수를 셉니다. */
    long countByStatus(OperationTaskStatus status);
}
