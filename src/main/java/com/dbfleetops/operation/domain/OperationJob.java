package com.dbfleetops.operation.domain;

import java.time.LocalDateTime;

public class OperationJob {

    private final Long id;
    private final JobType jobType;
    private final Long targetDatabaseId;
    private JobStatus status;
    private final String requestedBy;
    private final String idempotencyKey;

    private int retryCount;
    private final int maxRetryCount;

    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime availableAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private String resultCode;
    private String resultMessage;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private OperationJob(
            Long id,
            JobType jobType,
            Long targetDatabaseId,
            String requestedBy,
            String idempotencyKey,
            int maxRetryCount
    ) {
        this.id = id;
        this.jobType = jobType;
        this.targetDatabaseId = targetDatabaseId;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.status = JobStatus.QUEUED;
        this.retryCount = 0;
        this.maxRetryCount = maxRetryCount;
        this.availableAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static OperationJob create(
            JobType jobType,
            Long targetDatabaseId,
            String requestedBy,
            String idempotencyKey
    ) {
        return new OperationJob(
                null,
                jobType,
                targetDatabaseId,
                requestedBy,
                idempotencyKey,
                3
        );
    }

    public void start(
            String workerId,
            LocalDateTime leaseUntil
    ) {
        if (status != JobStatus.QUEUED) {
            throw new IllegalStateException(
                    "Only QUEUED job can be started. currentStatus=" + status
            );
        }

        this.status = JobStatus.RUNNING;
        this.leaseOwner = workerId;
        this.leaseUntil = leaseUntil;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void succeed(
            String resultMessage
    ) {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING job can be succeeded. currentStatus=" + status
            );
        }

        this.status = JobStatus.SUCCEEDED;
        this.resultCode = "SUCCESS";
        this.resultMessage = resultMessage;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(
            String resultCode,
            String resultMessage
    ) {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING job can be failed. currentStatus=" + status
            );
        }

        this.status = JobStatus.FAILED;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void retry(
            LocalDateTime nextAvailableAt
    ) {
        if (status != JobStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED job can be retried. currentStatus=" + status
            );
        }

        if (retryCount >= maxRetryCount) {
            throw new IllegalStateException(
                    "Retry count exceeded. retryCount=" + retryCount
                            + ", maxRetryCount=" + maxRetryCount
            );
        }

        this.retryCount++;
        this.status = JobStatus.QUEUED;
        this.availableAt = nextAvailableAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.finishedAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == JobStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "SUCCEEDED job cannot be cancelled."
            );
        }

        if (status == JobStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Already cancelled job."
            );
        }

        this.status = JobStatus.CANCELLED;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void timeout() {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING job can be timed out. currentStatus=" + status
            );
        }

        this.status = JobStatus.TIMED_OUT;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public JobType getJobType() {
        return jobType;
    }

    public Long getTargetDatabaseId() {
        return targetDatabaseId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public LocalDateTime getLeaseUntil() {
        return leaseUntil;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public String getResultCode() {
        return resultCode;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}