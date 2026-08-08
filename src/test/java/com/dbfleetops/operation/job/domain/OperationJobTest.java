package com.dbfleetops.operation.job.domain;


import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 8, 0, 0);

    @Test
    void createInitializesQueuedJob() {
        OperationJob job = OperationJob.create(
                JobType.BACKUP,
                1L,
                "local-user",
                "idem-001",
                NOW
        );

        assertThat(job.getJobType())
                .isEqualTo(JobType.BACKUP);

        assertThat(job.getTargetDatabaseId())
                .isEqualTo(1L);

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.QUEUED);

        assertThat(job.getRetryCount())
                .isZero();

        assertThat(job.getMaxRetryCount())
                .isEqualTo(3);

        assertThat(job.getRequestedBy())
                .isEqualTo("local-user");

        assertThat(job.getIdempotencyKey())
                .isEqualTo("idem-001");

        assertThat(job.getCreatedAt()).isEqualTo(NOW);
        assertThat(job.getUpdatedAt()).isEqualTo(NOW);
        assertThat(job.getAvailableAt()).isEqualTo(NOW);
    }

    @Test
    void startChangesStatusToRunningAndSetsLease() {
        OperationJob job = newBackupJob();

        LocalDateTime leaseUntil =
                NOW.plusSeconds(60);

        job.start(
                "worker-1",
                NOW,
                leaseUntil
        );

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(job.getLeaseOwner())
                .isEqualTo("worker-1");

        assertThat(job.getLeaseUntil())
                .isEqualTo(leaseUntil);

        assertThat(job.getStartedAt())
                .isEqualTo(NOW);

        assertThat(job.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void startThrowsExceptionWhenJobIsNotQueued() {
        OperationJob job = newBackupJob();

        job.start(
                "worker-1",
                NOW,
                NOW.plusSeconds(60)
        );

        assertThrows(
                IllegalStateException.class,
                () -> job.start(
                        "worker-2",
                        NOW,
                        NOW.plusSeconds(60)
                )
        );
    }

    @Test
    void succeedChangesStatusToSucceeded() {
        OperationJob job = runningJob();

        job.succeed(NOW.plusSeconds(10), "backup completed");

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.SUCCEEDED);

        assertThat(job.getResultCode())
                .isEqualTo("SUCCESS");

        assertThat(job.getResultMessage())
                .isEqualTo("backup completed");

        assertThat(job.getFinishedAt())
                .isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void succeedThrowsExceptionWhenJobIsNotRunning() {
        OperationJob job = newBackupJob();

        assertThrows(
                IllegalStateException.class,
                () -> job.succeed(NOW, "backup completed")
        );
    }

    @Test
    void failChangesStatusToFailed() {
        OperationJob job = runningJob();

        job.fail(
                NOW.plusSeconds(10),
                "BACKUP_FAILED",
                "mysqldump failed"
        );

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.FAILED);

        assertThat(job.getResultCode())
                .isEqualTo("BACKUP_FAILED");

        assertThat(job.getResultMessage())
                .isEqualTo("mysqldump failed");

        assertThat(job.getFinishedAt())
                .isNotNull();
    }

    @Test
    void retryChangesFailedJobToQueued() {
        OperationJob job = runningJob();

        job.fail(
                NOW.plusSeconds(10),
                "BACKUP_FAILED",
                "mysqldump failed"
        );

        LocalDateTime nextAvailableAt =
                NOW.plusSeconds(30);

        job.retry(NOW.plusSeconds(11), nextAvailableAt);

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.QUEUED);

        assertThat(job.getRetryCount())
                .isEqualTo(1);

        assertThat(job.getAvailableAt())
                .isEqualTo(nextAvailableAt);

        assertThat(job.getLeaseOwner())
                .isNull();

        assertThat(job.getLeaseUntil())
                .isNull();

        assertThat(job.getFinishedAt())
                .isNull();
    }

    @Test
    void retryThrowsExceptionWhenJobIsNotFailed() {
        OperationJob job = newBackupJob();

        assertThrows(
                IllegalStateException.class,
                () -> job.retry(
                        NOW,
                        NOW.plusSeconds(30)
                )
        );
    }

    @Test
    void cancelChangesStatusToCancelled() {
        OperationJob job = newBackupJob();

        job.cancel(NOW.plusSeconds(10));

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.CANCELLED);

        assertThat(job.getFinishedAt())
                .isNotNull();
    }

    @Test
    void cancelThrowsExceptionWhenJobSucceeded() {
        OperationJob job = runningJob();

        job.succeed(NOW.plusSeconds(10), "completed");

        assertThrows(
                IllegalStateException.class,
                () -> job.cancel(NOW.plusSeconds(11))
        );
    }

    @Test
    void timeoutChangesRunningJobToTimedOut() {
        OperationJob job = runningJob();

        job.timeout(NOW.plusSeconds(10));

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.TIMED_OUT);

        assertThat(job.getFinishedAt())
                .isNotNull();
    }

    @Test
    void timeoutThrowsExceptionWhenJobIsNotRunning() {
        OperationJob job = newBackupJob();

        assertThrows(
                IllegalStateException.class,
                () -> job.timeout(NOW)
        );
    }

    private OperationJob newBackupJob() {
        return OperationJob.create(
                JobType.BACKUP,
                1L,
                "local-user",
                "idem-001",
                NOW
        );
    }

    private OperationJob runningJob() {
        OperationJob job = newBackupJob();

        job.start(
                "worker-1",
                NOW,
                NOW.plusSeconds(60)
        );

        return job;
    }
}
