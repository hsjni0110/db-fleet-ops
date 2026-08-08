package com.dbfleetops.operation.job.application.provided;

import com.dbfleetops.operation.job.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.job.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.operation.job.dto.CreateConfigurationCheckJobRequest;
import com.dbfleetops.operation.job.dto.OperationJobResponse;
import java.util.List;

/**
 * 사용자의 운영 Job 요청을 받는 입구입니다.
 * HTTP Controller는 구체적인 Service 대신 이 인터페이스를 사용합니다.
 */
public interface JobOperations {
    /** 지정한 Database를 백업하는 Job을 만들거나, 같은 요청이 있으면 기존 Job을 반환합니다. */
    OperationJobResponse createBackupJob(Long databaseId, String idempotencyKey,
            CreateBackupJobRequest request);

    /** 지정한 Database의 현재 설정을 점검하는 Job을 만듭니다. */
    OperationJobResponse createConfigurationCheckJob(Long databaseId, String idempotencyKey,
            CreateConfigurationCheckJobRequest request);

    /** 검증을 통과한 설정값을 Database에 적용하는 Job을 만듭니다. */
    OperationJobResponse createConfigurationApplyJob(Long databaseId, String idempotencyKey,
            CreateConfigurationApplyJobRequest request);

    /** Job ID로 하나의 Job과 현재 상태를 조회합니다. */
    OperationJobResponse getJob(Long jobId);

    /** 최근에 생성된 순서로 전체 Job 목록을 조회합니다. */
    List<OperationJobResponse> getJobs();
}
