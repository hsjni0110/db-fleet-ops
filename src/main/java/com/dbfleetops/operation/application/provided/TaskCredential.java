package com.dbfleetops.operation.application.provided;

import com.dbfleetops.operation.dto.ResolveTaskCredentialRequest;
import com.dbfleetops.operation.dto.TaskCredentialResponse;

/** Agent가 Task 실행 직전에 Database 접속 정보를 요청할 때 사용하는 입구입니다. */
public interface TaskCredential {
    /** Agent, Task, 실행 번호와 Lease를 확인한 뒤 이번 실행에 필요한 계정 정보를 반환합니다. */
    TaskCredentialResponse resolve(Long agentId, Long taskId,
            ResolveTaskCredentialRequest request);
}
