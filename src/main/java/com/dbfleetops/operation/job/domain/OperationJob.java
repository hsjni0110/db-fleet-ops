package com.dbfleetops.operation.job.domain;

import lombok.Getter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;

@Getter
@Entity
@Table(name = "operation_job",
                uniqueConstraints = {@UniqueConstraint(name = "uk_operation_job_idempotency",
                                columnNames = {"targetDatabaseId", "jobType", "idempotencyKey"})})
public class OperationJob {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Enumerated(EnumType.STRING)
        private JobType jobType;

        private Long targetDatabaseId;

        @Enumerated(EnumType.STRING)
        private JobStatus status;

        private String requestedBy;

        private String idempotencyKey;

        @Column(length = 2000)
        private String requestPayload;

        private int priority;

        private int retryCount;

        private int maxRetryCount;

        private String leaseOwner;

        private LocalDateTime leaseUntil;

        private LocalDateTime availableAt;

        private LocalDateTime startedAt;

        private LocalDateTime finishedAt;

        private String resultCode;

        @Column(length = 2000)
        private String resultMessage;

        @Version
        private Long version;

        private LocalDateTime createdAt;

        private LocalDateTime updatedAt;

        protected OperationJob() {}

        private OperationJob(JobType jobType, Long targetDatabaseId, String requestedBy,
                        String idempotencyKey, String requestPayload, int maxRetryCount,
                        LocalDateTime createdAt) {
                notNull(createdAt, "Job 생성 시각은 필수입니다.");
                this.jobType = jobType;
                this.targetDatabaseId = targetDatabaseId;
                this.requestedBy = requestedBy;
                this.idempotencyKey = idempotencyKey;
                this.requestPayload = requestPayload;
                this.status = JobStatus.QUEUED;
                this.priority = 0;
                this.retryCount = 0;
                this.maxRetryCount = maxRetryCount;
                this.availableAt = createdAt;
                this.createdAt = createdAt;
                this.updatedAt = createdAt;
        }

        public static OperationJob create(JobType jobType, Long targetDatabaseId,
                        String requestedBy, String idempotencyKey, LocalDateTime createdAt) {
                return create(jobType, targetDatabaseId, requestedBy, idempotencyKey, null,
                                createdAt);
        }

        public static OperationJob create(JobType jobType, Long targetDatabaseId,
                        String requestedBy, String idempotencyKey, String requestPayload,
                        LocalDateTime createdAt) {
                return new OperationJob(jobType, targetDatabaseId, requestedBy, idempotencyKey,
                                requestPayload, 3, createdAt);
        }

        public void start(String workerId, LocalDateTime startedAt, LocalDateTime leaseUntil) {
                state(status == JobStatus.QUEUED,
                                "대기 중인 작업만 시작할 수 있습니다. 현재 상태=" + status);
                notNull(startedAt, "Job 시작 시각은 필수입니다.");

                this.status = JobStatus.RUNNING;
                this.leaseOwner = workerId;
                this.leaseUntil = leaseUntil;
                this.startedAt = startedAt;
                this.updatedAt = startedAt;
        }

        public void succeed(LocalDateTime completedAt, String resultMessage) {
                state(status == JobStatus.RUNNING,
                                "실행 중인 작업만 성공 처리할 수 있습니다. 현재 상태=" + status);
                notNull(completedAt, "Job 완료 시각은 필수입니다.");

                this.status = JobStatus.SUCCEEDED;
                this.resultCode = "SUCCESS";
                this.resultMessage = resultMessage;
                this.finishedAt = completedAt;
                this.updatedAt = completedAt;
        }

        public void fail(LocalDateTime failedAt, String resultCode, String resultMessage) {
                state(status == JobStatus.RUNNING,
                                "실행 중인 작업만 실패 처리할 수 있습니다. 현재 상태=" + status);
                notNull(failedAt, "Job 실패 시각은 필수입니다.");

                this.status = JobStatus.FAILED;
                this.resultCode = resultCode;
                this.resultMessage = resultMessage;
                this.finishedAt = failedAt;
                this.updatedAt = failedAt;
        }

        public void retry(LocalDateTime retriedAt, LocalDateTime nextAvailableAt) {
                state(status == JobStatus.FAILED,
                                "실패한 작업만 재시도할 수 있습니다. 현재 상태=" + status);
                state(retryCount < maxRetryCount,
                                "최대 재시도 횟수를 초과했습니다. 현재 횟수=" + retryCount
                                                + ", 최대 횟수=" + maxRetryCount);
                notNull(retriedAt, "Job 재시도 결정 시각은 필수입니다.");
                notNull(nextAvailableAt, "Job 재실행 가능 시각은 필수입니다.");

                this.retryCount++;
                this.status = JobStatus.QUEUED;
                this.availableAt = nextAvailableAt;
                this.leaseOwner = null;
                this.leaseUntil = null;
                this.finishedAt = null;
                this.updatedAt = retriedAt;
        }

        public void cancel(LocalDateTime cancelledAt) {
                state(status != null, "작업 상태가 존재해야 합니다.");
                state(status != JobStatus.SUCCEEDED, "성공한 작업은 취소할 수 없습니다.");
                state(status != JobStatus.CANCELLED, "이미 취소된 작업입니다.");
                notNull(cancelledAt, "Job 취소 시각은 필수입니다.");

                this.status = JobStatus.CANCELLED;
                this.finishedAt = cancelledAt;
                this.updatedAt = cancelledAt;
        }

        public void timeout(LocalDateTime timedOutAt) {
                timeout(timedOutAt, "TIMED_OUT", "Operation timed out.");
        }

        public void timeout(LocalDateTime timedOutAt, String resultCode, String resultMessage) {
                state(status == JobStatus.RUNNING,
                                "실행 중인 작업만 시간 초과 처리할 수 있습니다. 현재 상태=" + status);
                notNull(timedOutAt, "Job 시간 초과 시각은 필수입니다.");

                this.status = JobStatus.TIMED_OUT;
                this.resultCode = resultCode;
                this.resultMessage = resultMessage;
                this.finishedAt = timedOutAt;
                this.updatedAt = timedOutAt;
        }

        public void extendLease(LocalDateTime now, LocalDateTime newLeaseUntil) {
                state(status == JobStatus.RUNNING, "실행 중인 Job만 Lease를 연장할 수 있습니다.");
                state(leaseUntil != null && !leaseUntil.isAfter(now), "Job Lease가 아직 유효합니다.");
                state(newLeaseUntil.isAfter(now), "새 Lease 만료 시각은 현재보다 뒤여야 합니다.");
                this.leaseUntil = newLeaseUntil;
                this.updatedAt = now;
        }

        public void requeueExpiredLease(LocalDateTime now, LocalDateTime availableAt) {
                state(status == JobStatus.RUNNING, "실행 중인 Job만 재대기할 수 있습니다.");
                state(leaseUntil != null && !leaseUntil.isAfter(now), "Job Lease가 아직 유효합니다.");
                state(retryCount < maxRetryCount, "최대 재시도 횟수에 도달했습니다.");
                retryCount++;
                status = JobStatus.QUEUED;
                this.availableAt = availableAt;
                leaseOwner = null;
                leaseUntil = null;
                startedAt = null;
                updatedAt = now;
        }

        public boolean hasRemainingRetries() {
                return retryCount < maxRetryCount;
        }

}
