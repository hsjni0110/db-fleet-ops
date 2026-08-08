package com.dbfleetops.operation.task.domain;


import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class OperationTaskResultReportTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 8, 12, 0);
    private static final String REPORT_ID = "8d77288c-cf64-4ae8-a5be-a4010192fc6e";

    @Test
    void identicalSuccessReportIsAcceptedOnceAndThenDetectedAsDuplicate() {
        OperationTask task = runningTask();

        assertThat(task.acceptSuccessReport(1, REPORT_ID, "fingerprint", "{}", NOW))
                .isEqualTo(ResultReportAcceptance.ACCEPTED);
        assertThat(task.acceptSuccessReport(1, REPORT_ID, "fingerprint", "{}", NOW.plusSeconds(1)))
                .isEqualTo(ResultReportAcceptance.DUPLICATE);
        assertThat(task.getResultReportType()).isEqualTo(ResultReportType.SUCCESS);
    }

    @Test
    void identicalFailureReportIsAcceptedOnceAndThenDetectedAsDuplicate() {
        OperationTask task = runningTask();

        assertThat(task.acceptFailureReport(1, REPORT_ID, "fingerprint", "ERROR", "failed", NOW))
                .isEqualTo(ResultReportAcceptance.ACCEPTED);
        assertThat(task.acceptFailureReport(1, REPORT_ID, "fingerprint", "ERROR", "failed",
                NOW.plusSeconds(1))).isEqualTo(ResultReportAcceptance.DUPLICATE);
        assertThat(task.getResultReportType()).isEqualTo(ResultReportType.FAILURE);
    }

    @Test
    void changedIdentityTypePayloadOrAttemptIsRejected() {
        OperationTask task = runningTask();
        task.acceptSuccessReport(1, REPORT_ID, "fingerprint", "{}", NOW);

        assertThatIllegalStateException().isThrownBy(() -> task.acceptSuccessReport(1,
                "3b42dbed-c531-4dc8-b235-e2945dd52c90", "fingerprint", "{}", NOW));
        assertThatIllegalStateException().isThrownBy(() -> task.acceptSuccessReport(1,
                REPORT_ID, "changed", "{\"changed\":true}", NOW));
        assertThatIllegalStateException().isThrownBy(() -> task.acceptFailureReport(1,
                REPORT_ID, "fingerprint", "ERROR", "failed", NOW));
        assertThatIllegalStateException().isThrownBy(() -> task.acceptSuccessReport(2,
                REPORT_ID, "fingerprint", "{}", NOW));
    }

    private OperationTask runningTask() {
        OperationTask task = OperationTask.create(1L,
                OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        task.claim(NOW.minusSeconds(10), NOW.plusSeconds(60));
        return task;
    }
}
