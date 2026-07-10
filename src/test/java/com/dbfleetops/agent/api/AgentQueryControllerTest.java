package com.dbfleetops.agent.api;

import com.dbfleetops.agent.application.AgentQueryService;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.dto.AgentConsoleResponse;
import com.dbfleetops.agent.dto.AgentDetailResponse;
import com.dbfleetops.agent.dto.AgentHostMetricResponse;
import com.dbfleetops.operation.domain.OperationTaskStatus;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.OperationTaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentQueryController.class)
class AgentQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentQueryService agentQueryService;

    @Test
    void findAllReturnsAgentsWithoutToken() throws Exception {
        when(agentQueryService.findAll())
                .thenReturn(List.of(new AgentConsoleResponse(
                        1L,
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "amd64",
                        "0.1.0",
                        AgentStatus.ONLINE,
                        LocalDateTime.of(2026, 7, 10, 10, 0),
                        15L,
                        LocalDateTime.of(2026, 7, 10, 9, 0),
                        LocalDateTime.of(2026, 7, 10, 10, 0)
                )));

        mockMvc.perform(get("/api/v1/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentId").value(1))
                .andExpect(jsonPath("$[0].agentName").value("local-agent"))
                .andExpect(jsonPath("$[0].architecture").value("amd64"))
                .andExpect(jsonPath("$[0].heartbeatDelaySeconds").value(15))
                .andExpect(jsonPath("$[0].agentToken").doesNotExist());
    }

    @Test
    void findByIdReturnsAgentDetail() throws Exception {
        AgentConsoleResponse agent =
                new AgentConsoleResponse(
                        1L,
                        "local-agent",
                        "localhost",
                        "127.0.0.1",
                        "Linux",
                        "amd64",
                        "0.1.0",
                        AgentStatus.ONLINE,
                        LocalDateTime.of(2026, 7, 10, 10, 0),
                        15L,
                        LocalDateTime.of(2026, 7, 10, 9, 0),
                        LocalDateTime.of(2026, 7, 10, 10, 0)
                );

        AgentHostMetricResponse metric =
                new AgentHostMetricResponse(
                        10L,
                        1L,
                        12.5,
                        61.2,
                        73.8,
                        LocalDateTime.of(2026, 7, 10, 10, 1)
                );

        OperationTaskResponse task =
                new OperationTaskResponse(
                        20L,
                        1L,
                        30L,
                        OperationTaskType.COLLECT_LINUX_STATUS,
                        OperationTaskStatus.SUCCEEDED,
                        "{}",
                        "{}",
                        null,
                        null,
                        LocalDateTime.of(2026, 7, 10, 10, 2),
                        LocalDateTime.of(2026, 7, 10, 10, 3),
                        LocalDateTime.of(2026, 7, 10, 10, 1)
                );

        when(agentQueryService.findById(1L))
                .thenReturn(new AgentDetailResponse(agent, List.of(metric), List.of(task)));

        mockMvc.perform(get("/api/v1/agents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent.agentId").value(1))
                .andExpect(jsonPath("$.agent.agentToken").doesNotExist())
                .andExpect(jsonPath("$.recentHostMetrics[0].metricId").value(10))
                .andExpect(jsonPath("$.recentOperationTasks[0].taskId").value(20))
                .andExpect(jsonPath("$.recentOperationTasks[0].operationJobId").value(30));
    }
}
