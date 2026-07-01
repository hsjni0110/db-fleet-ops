package com.dbfleetops.operation.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationJobTest {

    @Test
    void createInitializesQueuedJob() {
        OperationJob job = OperationJob.create(
                JobType.BACKUP,
                1L,
                "local-user",
                "idem-001"
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
    }

    @Test
    void startChangesStatusToRunningAndSetsLease() {
        OperationJob job = newBackupJob();

        LocalDateTime leaseUntil =
                LocalDateTime.now().plusSeconds(60);

        job.start(
                "worker-1",
                leaseUntil
        );

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(job.getLeaseOwner())
                .isEqualTo("worker-1");

        assertThat(job.getLeaseUntil())
                .isEqualTo(leaseUntil);

        assertThat(job.getStartedAt())
                .isNotNull();
    }

    @Test
    void startThrowsExceptionWhenJobIsNotQueued() {
        OperationJob job = newBackupJob();

        job.start(
                "worker-1",
                LocalDateTime.now().plusSeconds(60)
        );

        assertThrows(
                IllegalStateException.class,
                () -> job.start(
                        "worker-2",
                        LocalDateTime.now().plusSeconds(60)
                )
        );
    }

    @Test
    void succeedChangesStatusToSucceeded() {
        OperationJob job = runningJob();

        job.succeed("backup completed");

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.SUCCEEDED);

        assertThat(job.getResultCode())
                .isEqualTo("SUCCESS");

        assertThat(job.getResultMessage())
                .isEqualTo("backup completed");

        assertThat(job.getFinishedAt())
                .isNotNull();
    }

    @Test
    void succeedThrowsExceptionWhenJobIsNotRunning() {
        OperationJob job = newBackupJob();

        assertThrows(
                IllegalStateException.class,
                () -> job.succeed("backup completed")
        );
    }

    @Test
    void failChangesStatusToFailed() {
        OperationJob job = runningJob();

        job.fail(
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
                "BACKUP_FAILED",
                "mysqldump failed"
        );

        LocalDateTime nextAvailableAt =
                LocalDateTime.now().plusSeconds(30);

        job.retry(nextAvailableAt);

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
                        LocalDateTime.now().plusSeconds(30)
                )
        );
    }

    @Test
    void cancelChangesStatusToCancelled() {
        OperationJob job = newBackupJob();

        job.cancel();

        assertThat(job.getStatus())
                .isEqualTo(JobStatus.CANCELLED);

        assertThat(job.getFinishedAt())
                .isNotNull();
    }

    @Test
    void cancelThrowsExceptionWhenJobSucceeded() {
        OperationJob job = runningJob();

        job.succeed("completed");

        assertThrows(
                IllegalStateException.class,
                job::cancel
        );
    }

    @Test
    void timeoutChangesRunningJobToTimedOut() {
        OperationJob job = runningJob();

        job.timeout();

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
                job::timeout
        );
    }

    private OperationJob newBackupJob() {
        return OperationJob.create(
                JobType.BACKUP,
                1L,
                "local-user",
                "idem-001"
        );
    }

    private OperationJob runningJob() {
        OperationJob job = newBackupJob();

        job.start(
                "worker-1",
                LocalDateTime.now().plusSeconds(60)
        );

        return job;
    }
}