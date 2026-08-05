package com.dbfleetops.operation.domain;

import lombok.Getter;

import jakarta.persistence.*;

import java.time.LocalDateTime;

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
                        String idempotencyKey, String requestPayload, int maxRetryCount) {
                this.jobType = jobType;
                this.targetDatabaseId = targetDatabaseId;
                this.requestedBy = requestedBy;
                this.idempotencyKey = idempotencyKey;
                this.requestPayload = requestPayload;
                this.status = JobStatus.QUEUED;
                this.priority = 0;
                this.retryCount = 0;
                this.maxRetryCount = maxRetryCount;
                this.availableAt = LocalDateTime.now();
                this.createdAt = LocalDateTime.now();
                this.updatedAt = LocalDateTime.now();
        }

        public static OperationJob create(JobType jobType, Long targetDatabaseId,
                        String requestedBy, String idempotencyKey) {
                return create(jobType, targetDatabaseId, requestedBy, idempotencyKey, null);
        }

        public static OperationJob create(JobType jobType, Long targetDatabaseId,
                        String requestedBy, String idempotencyKey, String requestPayload) {
                return new OperationJob(jobType, targetDatabaseId, requestedBy, idempotencyKey,
                                requestPayload, 3);
        }

        public void start(String workerId, LocalDateTime leaseUntil) {
                state(status == JobStatus.QUEUED,
                                "대기 중인 작업만 시작할 수 있습니다. 현재 상태=" + status);

                this.status = JobStatus.RUNNING;
                this.leaseOwner = workerId;
                this.leaseUntil = leaseUntil;
                this.startedAt = LocalDateTime.now();
                this.updatedAt = LocalDateTime.now();
        }

        public void succeed(String resultMessage) {
                state(status == JobStatus.RUNNING,
                                "실행 중인 작업만 성공 처리할 수 있습니다. 현재 상태=" + status);

                this.status = JobStatus.SUCCEEDED;
                this.resultCode = "SUCCESS";
                this.resultMessage = resultMessage;
                this.finishedAt = LocalDateTime.now();
                this.updatedAt = LocalDateTime.now();
        }

        public void fail(String resultCode, String resultMessage) {
                state(status == JobStatus.RUNNING,
                                "실행 중인 작업만 실패 처리할 수 있습니다. 현재 상태=" + status);

                this.status = JobStatus.FAILED;
                this.resultCode = resultCode;
                this.resultMessage = resultMessage;
                this.finishedAt = LocalDateTime.now();
                this.updatedAt = LocalDateTime.now();
        }

        public void retry(LocalDateTime nextAvailableAt) {
                state(status == JobStatus.FAILED,
                                "실패한 작업만 재시도할 수 있습니다. 현재 상태=" + status);
                state(retryCount < maxRetryCount,
                                "최대 재시도 횟수를 초과했습니다. 현재 횟수=" + retryCount
                                                + ", 최대 횟수=" + maxRetryCount);

                this.retryCount++;
                this.status = JobStatus.QUEUED;
                this.availableAt = nextAvailableAt;
                this.leaseOwner = null;
                this.leaseUntil = null;
                this.finishedAt = null;
                this.updatedAt = LocalDateTime.now();
        }

        public void cancel() {
                state(status != null, "작업 상태가 존재해야 합니다.");
                state(status != JobStatus.SUCCEEDED, "성공한 작업은 취소할 수 없습니다.");
                state(status != JobStatus.CANCELLED, "이미 취소된 작업입니다.");

                this.status = JobStatus.CANCELLED;
                this.finishedAt = LocalDateTime.now();
                this.updatedAt = LocalDateTime.now();
        }

        public void timeout() {
                state(status == JobStatus.RUNNING,
                                "실행 중인 작업만 시간 초과 처리할 수 있습니다. 현재 상태=" + status);

                this.status = JobStatus.TIMED_OUT;
                this.finishedAt = LocalDateTime.now();
                this.updatedAt = LocalDateTime.now();
        }

}
