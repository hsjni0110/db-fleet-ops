package com.dbfleetops.operation.task.application.required;

import com.dbfleetops.operation.task.domain.OperationTask;
import com.dbfleetops.operation.task.domain.OperationTaskType;

/**
 * Task 결과 접수 기능이 성공 후속 처리를 요청할 때 사용하는 출구입니다.
 * 각 integration Adapter는 자신이 이해하는 Task 종류의 성공 결과를 처리합니다.
 */
public interface TaskSuccessHandler {

    /** 이 처리기가 성공 결과를 이해할 수 있는 Task 종류인지 확인합니다. */
    boolean supports(OperationTaskType taskType);

    /** 최초로 접수된 Task 성공 결과의 후속 처리를 수행합니다. */
    void handleSuccess(OperationTask task, String resultPayloadJson);
}
