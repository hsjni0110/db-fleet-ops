package com.dbfleetops.operation.domain;

import lombok.Getter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

@Getter
@Entity
@Table(name = "operation_task")
public class OperationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;

    private Long operationJobId;

    @Enumerated(EnumType.STRING)
    private OperationTaskType taskType;

    @Enumerated(EnumType.STRING)
    private OperationTaskStatus status;

    @Column(length = 2000)
    private String parametersJson;

    @Column(length = 4000)
    private String resultPayloadJson;

    private String errorCode;

    @Column(length = 2000)
    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected OperationTask() {}

    private OperationTask(Long agentId, Long operationJobId, OperationTaskType taskType,
            String parametersJson) {
        notNull(agentId, "Agent 식별자는 필수입니다.");
        notNull(taskType, "작업 유형은 필수입니다.");

        this.agentId = agentId;
        this.operationJobId = operationJobId;
        this.taskType = taskType;
        this.parametersJson = parametersJson;
        this.status = OperationTaskStatus.QUEUED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static OperationTask create(Long agentId, OperationTaskType taskType,
            String parametersJson) {
        return new OperationTask(agentId, null, taskType, parametersJson);
    }

    public static OperationTask createForJob(Long agentId, Long operationJobId,
            OperationTaskType taskType, String parametersJson) {
        notNull(operationJobId, "Operation Job 식별자는 필수입니다.");

        return new OperationTask(agentId, operationJobId, taskType, parametersJson);
    }

    public void start() {
        state(status == OperationTaskStatus.QUEUED,
                "대기 중인 Task만 시작할 수 있습니다. 현재 상태=" + status);

        this.status = OperationTaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void succeed(String resultPayloadJson) {
        state(status == OperationTaskStatus.RUNNING,
                "실행 중인 Task만 성공 처리할 수 있습니다. 현재 상태=" + status);

        this.status = OperationTaskStatus.SUCCEEDED;
        this.resultPayloadJson = resultPayloadJson;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, String errorMessage) {
        state(status == OperationTaskStatus.RUNNING,
                "실행 중인 Task만 실패 처리할 수 있습니다. 현재 상태=" + status);
        hasText(errorCode, "오류 코드는 필수입니다.");

        this.status = OperationTaskStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

}
