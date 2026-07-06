package com.dbfleetops.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTaskTest {

        @Test
        void createInitializesQueuedTask() {
                AgentTask task = AgentTask.create(1L, AgentTaskType.COLLECT_LINUX_STATUS, "{}");

                assertThat(task.getAgentId()).isEqualTo(1L);

                assertThat(task.getTaskType()).isEqualTo(AgentTaskType.COLLECT_LINUX_STATUS);

                assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.QUEUED);

                assertThat(task.getParametersJson()).isEqualTo("{}");
        }

        @Test
        void startChangesQueuedTaskToRunning() {
                AgentTask task = newLinuxStatusTask();

                task.start();

                assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.RUNNING);

                assertThat(task.getStartedAt()).isNotNull();
        }

        @Test
        void completeChangesRunningTaskToSucceeded() {
                AgentTask task = newLinuxStatusTask();

                task.start();

                task.complete("{\"cpuUsagePercent\":12.5}");

                assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.SUCCEEDED);

                assertThat(task.getResultPayloadJson()).isEqualTo("{\"cpuUsagePercent\":12.5}");

                assertThat(task.getCompletedAt()).isNotNull();
        }

        @Test
        void failChangesRunningTaskToFailed() {
                AgentTask task = newLinuxStatusTask();

                task.start();

                task.fail("LINUX_STATUS_FAILED", "failed to read /proc/stat");

                assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.FAILED);

                assertThat(task.getErrorCode()).isEqualTo("LINUX_STATUS_FAILED");

                assertThat(task.getErrorMessage()).isEqualTo("failed to read /proc/stat");
        }

        @Test
        void completeThrowsExceptionWhenTaskIsNotRunning() {
                AgentTask task = newLinuxStatusTask();

                assertThrows(IllegalStateException.class, () -> task.complete("{}"));
        }

        @Test
        void failThrowsExceptionWhenTaskIsNotRunning() {
                AgentTask task = newLinuxStatusTask();

                assertThrows(IllegalStateException.class, () -> task.fail("ERROR", "failed"));
        }

        private AgentTask newLinuxStatusTask() {
                return AgentTask.create(1L, AgentTaskType.COLLECT_LINUX_STATUS, "{}");
        }

        @Test
        void createForOperationJobInitializesOperationJobId() {
                AgentTask task = AgentTask.createForOperationJob(1L, 100L,
                                AgentTaskType.MYSQL_LOGICAL_BACKUP,
                                "{\"databaseName\":\"orders\"}");

                assertThat(task.getAgentId()).isEqualTo(1L);

                assertThat(task.getOperationJobId()).isEqualTo(100L);

                assertThat(task.getTaskType()).isEqualTo(AgentTaskType.MYSQL_LOGICAL_BACKUP);

                assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.QUEUED);

                assertThat(task.getParametersJson()).isEqualTo("{\"databaseName\":\"orders\"}");
        }
}
