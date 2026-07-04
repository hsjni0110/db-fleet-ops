package com.dbfleetops.agent.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_task")
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;

    @Enumerated(EnumType.STRING)
    private AgentTaskType taskType;

    @Enumerated(EnumType.STRING)
    private AgentTaskStatus status;

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

    protected AgentTask() {
    }

    private AgentTask(
            Long agentId,
            AgentTaskType taskType,
            String parametersJson
    ) {
        this.agentId = agentId;
        this.taskType = taskType;
        this.parametersJson = parametersJson;
        this.status = AgentTaskStatus.QUEUED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static AgentTask create(
            Long agentId,
            AgentTaskType taskType,
            String parametersJson
    ) {
        return new AgentTask(
                agentId,
                taskType,
                parametersJson
        );
    }

    public void start() {
        if (status != AgentTaskStatus.QUEUED) {
            throw new IllegalStateException(
                    "Only QUEUED task can be started. currentStatus=" + status
            );
        }

        this.status = AgentTaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void complete(
            String resultPayloadJson
    ) {
        if (status != AgentTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING task can be completed. currentStatus=" + status
            );
        }

        this.status = AgentTaskStatus.SUCCEEDED;
        this.resultPayloadJson = resultPayloadJson;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(
            String errorCode,
            String errorMessage
    ) {
        if (status != AgentTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING task can be failed. currentStatus=" + status
            );
        }

        this.status = AgentTaskStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == AgentTaskStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "SUCCEEDED task cannot be cancelled."
            );
        }

        if (status == AgentTaskStatus.FAILED) {
            throw new IllegalStateException(
                    "FAILED task cannot be cancelled."
            );
        }

        this.status = AgentTaskStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public AgentTaskType getTaskType() {
        return taskType;
    }

    public AgentTaskStatus getStatus() {
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