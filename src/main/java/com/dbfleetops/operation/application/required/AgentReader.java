package com.dbfleetops.operation.application.required;

import java.util.Optional;

/** Operation이 Task를 실행할 Agent를 확인할 때 사용하는 출구입니다. */
public interface AgentReader {
    /** Agent ID로 Agent를 조회하며, 존재하지 않으면 빈 값을 반환합니다. */
    Optional<AgentExecutionTarget> findAgent(Long agentId);

    /** Agent가 보낸 Token이 등록된 값과 같은지 확인합니다. */
    boolean matchesToken(Long agentId, String agentToken);
}
