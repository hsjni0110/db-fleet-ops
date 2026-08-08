package com.dbfleetops.operation.application.required;

import com.dbfleetops.operation.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Operation이 Job을 저장하고 조회할 때 사용하는 출구입니다.
 * 실제 DB 접근 방식은 Adapter가 담당하며, 이 인터페이스에는 JPA 같은 기술을 드러내지 않습니다.
 */
public interface JobStore {
    /** 새 Job 또는 상태가 바뀐 Job을 저장합니다. */
    OperationJob save(OperationJob job);

    /** Job ID로 Job을 조회하며, 존재하지 않으면 빈 값을 반환합니다. */
    Optional<OperationJob> findById(Long id);

    /** Task 결과와 Job 상태를 함께 바꿀 때 Job을 잠가 조회합니다. */
    Optional<OperationJob> findByIdForUpdate(Long id);

    /** 같은 Database, Job 종류와 중복 방지 Key로 이미 만든 Job이 있는지 찾습니다. */
    Optional<OperationJob> findDuplicate(Long databaseId, JobType type, String idempotencyKey);

    /** 현재 실행할 수 있는 대기 Job을 우선순위와 생성 순서에 따라 제한된 개수만 조회합니다. */
    List<OperationJob> findClaimable(JobStatus status, LocalDateTime now, int limit);

    /** 최근에 생성된 Job부터 전체 목록을 조회합니다. */
    List<OperationJob> findLatest();

    /** Lease가 만료된 Job을 제한된 개수만 잠가서 조회합니다. */
    List<OperationJob> findExpiredForUpdate(JobStatus status, LocalDateTime now, int limit);

    /** 지정한 상태에 해당하는 Job 수를 셉니다. */
    long countByStatus(JobStatus status);
}
