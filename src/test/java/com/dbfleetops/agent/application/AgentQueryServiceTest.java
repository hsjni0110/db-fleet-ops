package com.dbfleetops.agent.application;

import com.dbfleetops.agent.domain.Agent;
import com.dbfleetops.agent.domain.AgentHostMetric;
import com.dbfleetops.agent.dto.AgentDetailResponse;
import com.dbfleetops.agent.infra.AgentHostMetricRepository;
import com.dbfleetops.agent.infra.AgentRepository;
import com.dbfleetops.operation.domain.OperationTask;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.infra.OperationTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentQueryServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentHostMetricRepository hostMetricRepository;

    @Mock
    private OperationTaskRepository operationTaskRepository;

    @Test
    void findByIdReturnsAgentMetricAndTaskSummaryWithoutToken() {
        Agent agent = Agent.register(
                "local-agent",
                "localhost",
                "127.0.0.1",
                "Linux",
                "amd64",
                "0.1.0",
                "agent-token-001"
        );
        ReflectionTestUtils.setField(agent, "id", 1L);

        AgentHostMetric metric = AgentHostMetric.create(1L, 12.5, 61.2, 73.8);
        ReflectionTestUtils.setField(metric, "id", 10L);

        OperationTask task = OperationTask.createForJob(
                1L,
                20L,
                OperationTaskType.COLLECT_LINUX_STATUS,
                "{}"
        );
        ReflectionTestUtils.setField(task, "id", 30L);

        when(agentRepository.findById(1L))
                .thenReturn(Optional.of(agent));
        when(hostMetricRepository.findTop10ByAgentIdOrderByCollectedAtDesc(1L))
                .thenReturn(List.of(metric));
        when(operationTaskRepository.findTop10ByAgentIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(task));

        AgentQueryService service =
                new AgentQueryService(
                        agentRepository,
                        hostMetricRepository,
                        operationTaskRepository
                );

        AgentDetailResponse response = service.findById(1L);

        assertThat(response.agent().agentId()).isEqualTo(1L);
        assertThat(response.agent().architecture()).isEqualTo("amd64");
        assertThat(response.recentHostMetrics()).hasSize(1);
        assertThat(response.recentOperationTasks()).hasSize(1);
        assertThat(response.recentOperationTasks().getFirst().operationJobId()).isEqualTo(20L);
    }
}
