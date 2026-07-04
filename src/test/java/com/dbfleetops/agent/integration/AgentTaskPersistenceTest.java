package com.dbfleetops.agent.integration;

import com.dbfleetops.agent.domain.AgentTask;
import com.dbfleetops.agent.domain.AgentTaskStatus;
import com.dbfleetops.agent.domain.AgentTaskType;
import com.dbfleetops.agent.infra.AgentTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AgentTaskPersistenceTest {

    @Autowired
    private AgentTaskRepository taskRepository;

    @Test
    void saveAndFindAgentTask() {
        AgentTask task =
                AgentTask.create(
                        1L,
                        AgentTaskType.COLLECT_LINUX_STATUS,
                        "{}"
                );

        AgentTask savedTask =
                taskRepository.save(task);

        AgentTask foundTask =
                taskRepository.findById(savedTask.getId())
                        .orElseThrow();

        assertThat(foundTask.getAgentId())
                .isEqualTo(1L);

        assertThat(foundTask.getTaskType())
                .isEqualTo(AgentTaskType.COLLECT_LINUX_STATUS);

        assertThat(foundTask.getStatus())
                .isEqualTo(AgentTaskStatus.QUEUED);

        assertThat(foundTask.getParametersJson())
                .isEqualTo("{}");
    }

    @Test
    void findQueuedTaskByAgentId() {
        AgentTask task =
                AgentTask.create(
                        1L,
                        AgentTaskType.COLLECT_LINUX_STATUS,
                        "{}"
                );

        taskRepository.save(task);

        List<AgentTask> foundTasks =
                taskRepository.findTop1ByAgentIdAndStatusOrderByCreatedAtAsc(
                        1L,
                        AgentTaskStatus.QUEUED
                );

        assertThat(foundTasks)
                .hasSize(1);

        assertThat(foundTasks.getFirst().getTaskType())
                .isEqualTo(AgentTaskType.COLLECT_LINUX_STATUS);
    }

    @Test
    void taskStartIsPersisted() {
        AgentTask task =
                AgentTask.create(
                        1L,
                        AgentTaskType.COLLECT_LINUX_STATUS,
                        "{}"
                );

        AgentTask savedTask =
                taskRepository.save(task);

        savedTask.start();

        taskRepository.flush();

        AgentTask foundTask =
                taskRepository.findById(savedTask.getId())
                        .orElseThrow();

        assertThat(foundTask.getStatus())
                .isEqualTo(AgentTaskStatus.RUNNING);

        assertThat(foundTask.getStartedAt())
                .isNotNull();
    }

    @Test
    void taskCompleteIsPersisted() {
        AgentTask task =
                AgentTask.create(
                        1L,
                        AgentTaskType.COLLECT_LINUX_STATUS,
                        "{}"
                );

        AgentTask savedTask =
                taskRepository.save(task);

        savedTask.start();
        savedTask.complete(
                "{\"cpuUsagePercent\":12.5}"
        );

        taskRepository.flush();

        AgentTask foundTask =
                taskRepository.findById(savedTask.getId())
                        .orElseThrow();

        assertThat(foundTask.getStatus())
                .isEqualTo(AgentTaskStatus.SUCCEEDED);

        assertThat(foundTask.getResultPayloadJson())
                .isEqualTo("{\"cpuUsagePercent\":12.5}");

        assertThat(foundTask.getCompletedAt())
                .isNotNull();
    }

    @Test
    void taskFailIsPersisted() {
        AgentTask task =
                AgentTask.create(
                        1L,
                        AgentTaskType.COLLECT_LINUX_STATUS,
                        "{}"
                );

        AgentTask savedTask =
                taskRepository.save(task);

        savedTask.start();
        savedTask.fail(
                "LINUX_STATUS_FAILED",
                "failed to read /proc/stat"
        );

        taskRepository.flush();

        AgentTask foundTask =
                taskRepository.findById(savedTask.getId())
                        .orElseThrow();

        assertThat(foundTask.getStatus())
                .isEqualTo(AgentTaskStatus.FAILED);

        assertThat(foundTask.getErrorCode())
                .isEqualTo("LINUX_STATUS_FAILED");

        assertThat(foundTask.getErrorMessage())
                .isEqualTo("failed to read /proc/stat");
    }
}