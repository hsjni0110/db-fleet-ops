package com.dbfleetops.agent.api;

import com.dbfleetops.agent.application.AgentService;
import com.dbfleetops.agent.application.AgentTaskService;
import com.dbfleetops.agent.domain.AgentStatus;
import com.dbfleetops.agent.domain.AgentTaskType;
import com.dbfleetops.agent.dto.AgentHeartbeatResponse;
import com.dbfleetops.agent.dto.NextAgentTaskResponse;
import com.dbfleetops.agent.dto.RegisterAgentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentInternalController.class)
class AgentInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentService agentService;

    @MockitoBean
    private AgentTaskService agentTaskService;

    @Test
    void registerReturnsAgentIdAndToken() throws Exception {
        when(agentService.register(any()))
                .thenReturn(new RegisterAgentResponse(
                        1L,
                        "agent-token-001",
                        AgentStatus.ONLINE
                ));

        String body = """
                {
                  "agentName": "local-agent",
                  "hostname": "localhost",
                  "ipAddress": "127.0.0.1",
                  "osName": "Linux",
                  "agentVersion": "0.1.0"
                }
                """;

        mockMvc.perform(post("/internal/v1/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(1))
                .andExpect(jsonPath("$.agentToken").value("agent-token-001"))
                .andExpect(jsonPath("$.status").value("ONLINE"));
    }

    @Test
    void heartbeatReturnsOnlineStatus() throws Exception {
        when(agentService.heartbeat(
                eq(1L),
                any()
        )).thenReturn(new AgentHeartbeatResponse(
                1L,
                AgentStatus.ONLINE,
                LocalDateTime.of(2026, 7, 4, 10, 0)
        ));

        String body = """
                {
                  "agentToken": "agent-token-001",
                  "cpuUsagePercent": 12.5,
                  "memoryUsagePercent": 61.2,
                  "diskUsagePercent": 73.8
                }
                """;

        mockMvc.perform(post("/internal/v1/agents/1/heartbeats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(1))
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andExpect(jsonPath("$.lastHeartbeatAt").exists());
    }

    @Test
    void nextTaskReturnsQueuedTask() throws Exception {
        when(agentTaskService.nextTask(
                eq(1L),
                eq("agent-token-001")
        )).thenReturn(new NextAgentTaskResponse(
                true,
                10L,
                AgentTaskType.COLLECT_LINUX_STATUS,
                "{}"
        ));

        mockMvc.perform(get("/internal/v1/agents/1/tasks/next")
                        .param("agentToken", "agent-token-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTask").value(true))
                .andExpect(jsonPath("$.taskId").value(10))
                .andExpect(jsonPath("$.taskType").value("COLLECT_LINUX_STATUS"));
    }
}