package com.dbfleetops.operation.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OperationTaskPersistenceTest {

        private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 12, 0);
        private static final String REPORT_ID = "8d77288c-cf64-4ae8-a5be-a4010192fc6e";

        private static void claim(OperationTask task) {
                task.claim(NOW, NOW.plusMinutes(1));
        }

        @Autowired
        private OperationTaskRepository taskRepository;

        @Test
        void saveAndFindOperationTask() {
                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                OperationTask savedTask = taskRepository.save(task);

                OperationTask foundTask = taskRepository.findById(savedTask.getId()).orElseThrow();

                assertThat(foundTask.getAgentId()).isEqualTo(1L);

                assertThat(foundTask.getTaskType())
                                .isEqualTo(OperationTaskType.COLLECT_LINUX_STATUS);

                assertThat(foundTask.getStatus()).isEqualTo(OperationTaskStatus.QUEUED);

                assertThat(foundTask.getParametersJson()).isEqualTo("{}");
        }

        @Test
        void findQueuedTaskByAgentId() {
                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                taskRepository.save(task);

                Optional<OperationTask> foundTask =
                                taskRepository.findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(1L,
                                                OperationTaskStatus.QUEUED);

                assertThat(foundTask).isPresent();

                assertThat(foundTask.orElseThrow().getTaskType())
                                .isEqualTo(OperationTaskType.COLLECT_LINUX_STATUS);
        }

        @Test
        void findLatestTasksByAgentId() {
                taskRepository.save(OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}"));

                taskRepository.save(OperationTask.create(1L,
                                OperationTaskType.MYSQL_LOGICAL_BACKUP, "{}"));

                List<OperationTask> foundTasks =
                                taskRepository.findTop10ByAgentIdOrderByCreatedAtDesc(1L);

                assertThat(foundTasks).hasSize(2);
        }

        @Test
        void taskStartIsPersisted() {
                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                OperationTask savedTask = taskRepository.save(task);

                claim(savedTask);

                taskRepository.flush();

                OperationTask foundTask = taskRepository.findById(savedTask.getId()).orElseThrow();

                assertThat(foundTask.getStatus()).isEqualTo(OperationTaskStatus.RUNNING);

                assertThat(foundTask.getStartedAt()).isNotNull();
        }

        @Test
        void taskCompleteIsPersisted() {
                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                OperationTask savedTask = taskRepository.save(task);

                claim(savedTask);
                savedTask.acceptSuccessReport(1, REPORT_ID, "success-fingerprint",
                                "{\"cpuUsagePercent\":12.5}", NOW.plusSeconds(10));

                taskRepository.flush();

                OperationTask foundTask = taskRepository.findById(savedTask.getId()).orElseThrow();

                assertThat(foundTask.getStatus()).isEqualTo(OperationTaskStatus.SUCCEEDED);

                assertThat(foundTask.getResultPayloadJson())
                                .isEqualTo("{\"cpuUsagePercent\":12.5}");

                assertThat(foundTask.getCompletedAt()).isNotNull();
        }

        @Test
        void taskFailIsPersisted() {
                OperationTask task = OperationTask.create(1L,
                                OperationTaskType.COLLECT_LINUX_STATUS, "{}");

                OperationTask savedTask = taskRepository.save(task);

                claim(savedTask);
                savedTask.acceptFailureReport(1, REPORT_ID, "failure-fingerprint",
                                "LINUX_STATUS_FAILED", "failed to read /proc/stat",
                                NOW.plusSeconds(10));

                taskRepository.flush();

                OperationTask foundTask = taskRepository.findById(savedTask.getId()).orElseThrow();

                assertThat(foundTask.getStatus()).isEqualTo(OperationTaskStatus.FAILED);

                assertThat(foundTask.getErrorCode()).isEqualTo("LINUX_STATUS_FAILED");

                assertThat(foundTask.getErrorMessage()).isEqualTo("failed to read /proc/stat");
        }
}
