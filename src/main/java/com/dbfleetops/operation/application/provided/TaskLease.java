package com.dbfleetops.operation.application.provided;

import com.dbfleetops.operation.dto.OperationTaskLeaseResponse;
import com.dbfleetops.operation.dto.RenewOperationTaskLeaseRequest;

/** Agent가 실행 중인 Task의 사용 시간을 연장할 때 사용하는 입구입니다. */
public interface TaskLease {
    /** 현재 실행 번호와 소유 Agent를 확인하고 Task Lease 만료 시각을 연장합니다. */
    OperationTaskLeaseResponse renew(Long agentId, Long taskId,
            RenewOperationTaskLeaseRequest request);
}
