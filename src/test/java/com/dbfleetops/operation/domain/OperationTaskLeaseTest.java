package com.dbfleetops.operation.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class OperationTaskLeaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 12, 0);

    @Test
    void claimStartsFirstExecutionAndCreatesLease() {
        OperationTask task = newTask();

        task.claim(NOW, NOW.plusSeconds(60));

        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.RUNNING);
        assertThat(task.getExecutionAttempt()).isEqualTo(1);
        assertThat(task.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(task.getLastProgressAt()).isEqualTo(NOW);
    }

    @Test
    void expiredTaskIsRequeuedAndClaimedWithNextAttempt() {
        OperationTask task = newTask();
        task.claim(NOW, NOW.plusSeconds(60));
        task.requeueExpiredLease(NOW.plusSeconds(60), 3);

        task.claim(NOW.plusSeconds(61), NOW.plusSeconds(121));

        assertThat(task.getExecutionAttempt()).isEqualTo(2);
        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.RUNNING);
    }

    @Test
    void renewLeaseRequiresCurrentExecutionAndUnexpiredLease() {
        OperationTask task = newTask();
        task.claim(NOW, NOW.plusSeconds(60));

        task.renewLease(1, NOW.plusSeconds(20), NOW.plusSeconds(80));

        assertThat(task.getLastProgressAt()).isEqualTo(NOW.plusSeconds(20));
        assertThat(task.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(80));
        assertThatIllegalStateException().isThrownBy(
                () -> task.renewLease(0, NOW.plusSeconds(30), NOW.plusSeconds(90)));
        assertThatIllegalStateException().isThrownBy(
                () -> task.renewLease(1, NOW.plusSeconds(80), NOW.plusSeconds(140)));
    }

    @Test
    void thirdExpiredExecutionTimesOut() {
        OperationTask task = newTask();
        for (int attempt = 1; attempt <= 3; attempt++) {
            LocalDateTime claimedAt = NOW.plusMinutes(attempt);
            task.claim(claimedAt, claimedAt.plusSeconds(60));
            if (attempt < 3) task.requeueExpiredLease(claimedAt.plusSeconds(60), 3);
            else task.timeoutExpiredLease(claimedAt.plusSeconds(60), 3);
        }

        assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.TIMED_OUT);
        assertThat(task.getErrorCode()).isEqualTo("TASK_LEASE_EXPIRED");
    }

    @Test
    void staleExecutionCannotCompleteOrFailTask() {
        OperationTask task = newTask();
        task.claim(NOW, NOW.plusSeconds(60));

        assertThatIllegalStateException().isThrownBy(
                () -> task.acceptSuccessReport(0, "report-id", "fingerprint", "{}",
                        NOW.plusSeconds(10)));
        assertThatIllegalStateException().isThrownBy(
                () -> task.acceptFailureReport(0, "report-id", "fingerprint", "ERROR", "failed",
                        NOW.plusSeconds(10)));
    }

    @Test
    void leaseExpiresAtExactBoundary() {
        OperationTask task = newTask();
        task.claim(NOW, NOW.plusSeconds(60));

        assertThatIllegalStateException().isThrownBy(
                () -> task.acceptSuccessReport(1, "report-id", "fingerprint", "{}",
                        NOW.plusSeconds(60)));
    }

    private OperationTask newTask() {
        return OperationTask.create(1L, OperationTaskType.COLLECT_LINUX_STATUS, "{}");
    }
}
