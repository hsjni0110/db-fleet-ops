package com.dbfleetops.operation.domain;

import lombok.Getter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

@Getter
@Entity
@Table(name = "operation_task", indexes = @Index(
        name = "idx_operation_task_status_lease_expires_at",
        columnList = "status, leaseExpiresAt"))
public class OperationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;

    private Long operationJobId;

    private Long credentialId;

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

    private int executionAttempt;

    private LocalDateTime leaseExpiresAt;

    private LocalDateTime lastProgressAt;

    @Column(length = 36)
    private String resultReportId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ResultReportType resultReportType;

    @Column(length = 64)
    private String resultReportFingerprint;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected OperationTask() {}

    private OperationTask(Long agentId, Long operationJobId, Long credentialId,
            OperationTaskType taskType, String parametersJson) {
        notNull(agentId, "Agent 식별자는 필수입니다.");
        notNull(taskType, "작업 유형은 필수입니다.");

        this.agentId = agentId;
        this.operationJobId = operationJobId;
        this.credentialId = credentialId;
        this.taskType = taskType;
        this.parametersJson = parametersJson;
        this.status = OperationTaskStatus.QUEUED;
        this.executionAttempt = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static OperationTask create(Long agentId, OperationTaskType taskType,
            String parametersJson) {
        return new OperationTask(agentId, null, null, taskType, parametersJson);
    }

    public static OperationTask createForJob(Long agentId, Long operationJobId,
            OperationTaskType taskType, String parametersJson) {
        notNull(operationJobId, "Operation Job 식별자는 필수입니다.");

        return new OperationTask(agentId, operationJobId, null, taskType, parametersJson);
    }

    public static OperationTask createForJob(Long agentId, Long operationJobId, Long credentialId,
            OperationTaskType taskType, String parametersJson) {
        notNull(operationJobId, "Operation Job 식별자는 필수입니다.");
        notNull(credentialId, "Credential 식별자는 필수입니다.");
        return new OperationTask(agentId, operationJobId, credentialId, taskType, parametersJson);
    }

    public void claim(LocalDateTime now, LocalDateTime leaseExpiresAt) {
        state(status == OperationTaskStatus.QUEUED,
                "대기 중인 Task만 시작할 수 있습니다. 현재 상태=" + status);
        notNull(now, "선점 시각은 필수입니다.");
        notNull(leaseExpiresAt, "Lease 만료 시각은 필수입니다.");
        state(leaseExpiresAt.isAfter(now), "Lease 만료 시각은 선점 시각보다 뒤여야 합니다.");

        this.status = OperationTaskStatus.RUNNING;
        this.executionAttempt++;
        this.startedAt = now;
        this.leaseExpiresAt = leaseExpiresAt;
        this.lastProgressAt = now;
        this.updatedAt = now;
    }

    public void renewLease(int attempt, LocalDateTime now, LocalDateTime newLeaseExpiresAt) {
        validateActiveExecution(attempt, now);
        notNull(newLeaseExpiresAt, "새 Lease 만료 시각은 필수입니다.");
        state(newLeaseExpiresAt.isAfter(now), "새 Lease 만료 시각은 현재 시각보다 뒤여야 합니다.");

        this.leaseExpiresAt = newLeaseExpiresAt;
        this.lastProgressAt = now;
        this.updatedAt = now;
    }

    public void validateCredentialAccess(int attempt, LocalDateTime now) {
        validateActiveExecution(attempt, now);
        notNull(credentialId, "Credential이 없는 Task입니다.");
    }

    public ResultReportAcceptance acceptSuccessReport(int attempt, String reportId,
            String fingerprint, String resultPayloadJson, LocalDateTime now) {
        if (status != OperationTaskStatus.RUNNING) {
            validateDuplicateReport(attempt, reportId, ResultReportType.SUCCESS, fingerprint);
            return ResultReportAcceptance.DUPLICATE;
        }
        validateActiveExecution(attempt, now);

        this.status = OperationTaskStatus.SUCCEEDED;
        this.resultReportId = reportId;
        this.resultReportType = ResultReportType.SUCCESS;
        this.resultReportFingerprint = fingerprint;
        this.resultPayloadJson = resultPayloadJson;
        this.completedAt = now;
        this.lastProgressAt = now;
        this.updatedAt = now;
        return ResultReportAcceptance.ACCEPTED;
    }

    public ResultReportAcceptance acceptFailureReport(int attempt, String reportId,
            String fingerprint, String errorCode, String errorMessage, LocalDateTime now) {
        if (status != OperationTaskStatus.RUNNING) {
            validateDuplicateReport(attempt, reportId, ResultReportType.FAILURE, fingerprint);
            return ResultReportAcceptance.DUPLICATE;
        }
        validateActiveExecution(attempt, now);
        hasText(errorCode, "오류 코드는 필수입니다.");

        this.status = OperationTaskStatus.FAILED;
        this.resultReportId = reportId;
        this.resultReportType = ResultReportType.FAILURE;
        this.resultReportFingerprint = fingerprint;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = now;
        this.lastProgressAt = now;
        this.updatedAt = now;
        return ResultReportAcceptance.ACCEPTED;
    }

    public void requeueExpiredLease(LocalDateTime now, int maximumAttempts) {
        validateExpiredLease(now);
        state(executionAttempt < maximumAttempts, "최대 실행 횟수에 도달한 Task는 재대기할 수 없습니다.");

        this.status = OperationTaskStatus.QUEUED;
        this.leaseExpiresAt = null;
        this.lastProgressAt = null;
        this.updatedAt = now;
    }

    public void timeoutExpiredLease(LocalDateTime now, int maximumAttempts) {
        validateExpiredLease(now);
        state(executionAttempt >= maximumAttempts, "실행 횟수가 남은 Task는 최종 시간 초과 처리할 수 없습니다.");

        this.status = OperationTaskStatus.TIMED_OUT;
        this.errorCode = "TASK_LEASE_EXPIRED";
        this.errorMessage = "Task lease expired after maximum execution attempts.";
        this.completedAt = now;
        this.updatedAt = now;
    }

    private void validateActiveExecution(int attempt, LocalDateTime now) {
        state(status == OperationTaskStatus.RUNNING,
                "실행 중인 Task만 처리할 수 있습니다. 현재 상태=" + status);
        state(executionAttempt == attempt,
                "현재 실행 번호와 일치하지 않습니다. 현재=" + executionAttempt + ", 요청=" + attempt);
        notNull(now, "현재 시각은 필수입니다.");
        state(leaseExpiresAt != null && leaseExpiresAt.isAfter(now), "Task Lease가 만료되었습니다.");
    }

    private void validateDuplicateReport(int attempt, String reportId, ResultReportType reportType,
            String fingerprint) {
        state(status == OperationTaskStatus.SUCCEEDED || status == OperationTaskStatus.FAILED,
                "완료된 성공 또는 실패 Task만 결과를 재보고할 수 있습니다. 현재 상태=" + status);
        state(executionAttempt == attempt,
                "현재 실행 번호와 일치하지 않습니다. 현재=" + executionAttempt + ", 요청=" + attempt);
        state(java.util.Objects.equals(resultReportId, reportId),
                "기존 결과 보고 식별자와 일치하지 않습니다.");
        state(resultReportType == reportType, "기존 결과 보고 종류와 일치하지 않습니다.");
        state(java.util.Objects.equals(resultReportFingerprint, fingerprint),
                "기존 결과 보고 내용과 일치하지 않습니다.");
    }

    private void validateExpiredLease(LocalDateTime now) {
        state(status == OperationTaskStatus.RUNNING,
                "실행 중인 Task만 Lease 만료 처리할 수 있습니다. 현재 상태=" + status);
        notNull(now, "현재 시각은 필수입니다.");
        state(leaseExpiresAt != null && !leaseExpiresAt.isAfter(now), "Task Lease가 아직 유효합니다.");
    }

}
