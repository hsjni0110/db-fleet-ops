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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OperationTaskPersistenceTest {

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

                List<OperationTask> foundTasks =
                                taskRepository.findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(1L,
                                                OperationTaskStatus.QUEUED);

                assertThat(foundTasks).hasSize(1);

                assertThat(foundTasks.getFirst().getTaskType())
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

                savedTask.start();

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

                savedTask.start();
                savedTask.complete("{\"cpuUsagePercent\":12.5}");

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

                savedTask.start();
                savedTask.fail("LINUX_STATUS_FAILED", "failed to read /proc/stat");

                taskRepository.flush();

                OperationTask foundTask = taskRepository.findById(savedTask.getId()).orElseThrow();

                assertThat(foundTask.getStatus()).isEqualTo(OperationTaskStatus.FAILED);

                assertThat(foundTask.getErrorCode()).isEqualTo("LINUX_STATUS_FAILED");

                assertThat(foundTask.getErrorMessage()).isEqualTo("failed to read /proc/stat");
        }
}
