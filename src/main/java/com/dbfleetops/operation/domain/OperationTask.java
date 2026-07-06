package com.dbfleetops.operation.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

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
        return new OperationTask(agentId, operationJobId, taskType, parametersJson);
    }

    public void start() {
        if (status != OperationTaskStatus.QUEUED) {
            throw new IllegalStateException(
                    "Only QUEUED task can be started. currentStatus=" + status);
        }

        this.status = OperationTaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void complete(String resultPayloadJson) {
        if (status != OperationTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING task can be completed. currentStatus=" + status);
        }

        this.status = OperationTaskStatus.SUCCEEDED;
        this.resultPayloadJson = resultPayloadJson;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, String errorMessage) {
        if (status != OperationTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING task can be failed. currentStatus=" + status);
        }

        this.status = OperationTaskStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public Long getOperationJobId() {
        return operationJobId;
    }

    public OperationTaskType getTaskType() {
        return taskType;
    }

    public OperationTaskStatus getStatus() {
        return status;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public String getResultPayloadJson() {
        return resultPayloadJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
