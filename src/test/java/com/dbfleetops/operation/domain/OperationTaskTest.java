package com.dbfleetops.operation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationTaskTest {

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
        void startChangesQueuedTaskToRunning() {
                OperationTask task = newLinuxStatusTask();

                task.start();

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.RUNNING);

                assertThat(task.getStartedAt()).isNotNull();
        }

        @Test
        void completeChangesRunningTaskToSucceeded() {
                OperationTask task = newLinuxStatusTask();

                task.start();

                task.complete("{\"cpuUsagePercent\":12.5}");

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(task.getResultPayloadJson()).isEqualTo("{\"cpuUsagePercent\":12.5}");

                assertThat(task.getCompletedAt()).isNotNull();
        }

        @Test
        void failChangesRunningTaskToFailed() {
                OperationTask task = newLinuxStatusTask();

                task.start();

                task.fail("LINUX_STATUS_FAILED", "failed to read /proc/stat");

                assertThat(task.getStatus()).isEqualTo(OperationTaskStatus.FAILED);

                assertThat(task.getErrorCode()).isEqualTo("LINUX_STATUS_FAILED");

                assertThat(task.getErrorMessage()).isEqualTo("failed to read /proc/stat");
        }

        @Test
        void completeThrowsExceptionWhenTaskIsNotRunning() {
                OperationTask task = newLinuxStatusTask();

                assertThrows(IllegalStateException.class, () -> task.complete("{}"));
        }

        @Test
        void failThrowsExceptionWhenTaskIsNotRunning() {
                OperationTask task = newLinuxStatusTask();

                assertThrows(IllegalStateException.class, () -> task.fail("ERROR", "failed"));
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
