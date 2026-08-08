package com.dbfleetops.operation.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class OperationTaskTest {
        private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 12, 0);
        private static final String REPORT_ID = "8d77288c-cf64-4ae8-a5be-a4010192fc6e";

        private static void claim(OperationTask task) {
                task.claim(NOW, NOW.plusMinutes(1));
        }

        @Test
        void createInitializesQueuedTask() {
                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                assertThat(task.getAgentId()).isEqualTo(1L);

                assertThat(task.getTaskType()).isEqualTo(OperationTaskType.COLLECT_LINUX_STATUS);

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.QUEUED);

                assertThat(task.getParametersJson()).isEqualTo("{}");
        }

        @Test
        void createRejectsMissingAgentId() {
                assertThatIllegalArgumentException()
                                .isThrownBy(() -> OperationTask.create(null,
                                                OperationTaskType.COLLECT_LINUX_STATUS, "{}"))
                                .withMessage("Agent 식별자는 필수입니다.");
        }

        @Test
        void createRejectsMissingTaskType() {
                assertThatIllegalArgumentException()
                                .isThrownBy(() -> OperationTask.create(1L, null, "{}"))
                                .withMessage("작업 유형은 필수입니다.");
        }

        @Test
        void createForJobRejectsMissingOperationJobId() {
                assertThatIllegalArgumentException()
                                .isThrownBy(() -> OperationTask.createForJob(1L, null,
                                                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}"))
                                .withMessage("Operation Job 식별자는 필수입니다.");
        }

        @Test
        void startChangesQueuedTaskToRunning() {
                OperationTask task = newLinuxStatusTask();

                claim(task);

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.RUNNING);

                assertThat(task.getStartedAt()).isNotNull();
        }

        @Test
        void startRejectsTaskThatIsNotQueued() {
                OperationTask task = newLinuxStatusTask();
                claim(task);

                assertThatIllegalStateException()
                                .isThrownBy(() -> claim(task))
                                .withMessage("대기 중인 Task만 시작할 수 있습니다. 현재 상태=RUNNING");
        }

        @Test
        void completeChangesRunningTaskToSucceeded() {
                OperationTask task = newLinuxStatusTask();

                claim(task);

                task.acceptSuccessReport(1, REPORT_ID, "success-fingerprint", "{\"cpuUsagePercent\":12.5}", NOW.plusSeconds(10));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(task.getResultPayloadJson()).isEqualTo("{\"cpuUsagePercent\":12.5}");

                assertThat(task.getCompletedAt()).isNotNull();
        }

        @Test
        void failChangesRunningTaskToFailed() {
                OperationTask task = newLinuxStatusTask();

                claim(task);

                task.acceptFailureReport(1, REPORT_ID, "failure-fingerprint", "LINUX_STATUS_FAILED", "failed to read /proc/stat", NOW.plusSeconds(10));

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.FAILED);

                assertThat(task.getErrorCode()).isEqualTo("LINUX_STATUS_FAILED");

                assertThat(task.getErrorMessage()).isEqualTo("failed to read /proc/stat");
        }

        @Test
        void completeThrowsExceptionWhenTaskIsNotRunning() {
                OperationTask task = newLinuxStatusTask();

                assertThatIllegalStateException()
                                .isThrownBy(() -> task.acceptSuccessReport(1, REPORT_ID, "success-fingerprint", "{}", NOW.plusSeconds(10)))
                                .withMessage("완료된 성공 또는 실패 Task만 결과를 재보고할 수 있습니다. 현재 상태=QUEUED");
        }

        @Test
        void failThrowsExceptionWhenTaskIsNotRunning() {
                OperationTask task = newLinuxStatusTask();

                assertThatIllegalStateException()
                                .isThrownBy(() -> task.acceptFailureReport(1, REPORT_ID, "failure-fingerprint", "ERROR", "failed", NOW.plusSeconds(10)))
                                .withMessage("완료된 성공 또는 실패 Task만 결과를 재보고할 수 있습니다. 현재 상태=QUEUED");
        }

        @Test
        void failRejectsMissingErrorCode() {
                OperationTask task = newLinuxStatusTask();
                claim(task);

                assertThatIllegalArgumentException()
                                .isThrownBy(() -> task.acceptFailureReport(1, REPORT_ID, "failure-fingerprint", " ", "failed", NOW.plusSeconds(10)))
                                .withMessage("오류 코드는 필수입니다.");
        }

        private OperationTask newLinuxStatusTask() {
                return OperationTask.create(1L, OperationTaskType.COLLECT_LINUX_STATUS, "{}");
        }

        @Test
        void createForOperationJobInitializesOperationJobId() {
                OperationTask task = OperationTask.createForJob(1L, 100L,
                                OperationTaskType.MYSQL_LOGICAL_BACKUP,
                                "{\"databaseName\":\"orders\"}");

                assertThat(task.getAgentId()).isEqualTo(1L);

                assertThat(task.getOperationJobId()).isEqualTo(100L);

                assertThat(task.getTaskType()).isEqualTo(OperationTaskType.MYSQL_LOGICAL_BACKUP);

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.QUEUED);

                assertThat(task.getParametersJson()).isEqualTo("{\"databaseName\":\"orders\"}");
        }
}
