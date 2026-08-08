package com.dbfleetops.operation.adapter.webapi;

import com.dbfleetops.operation.application.provided.*;
import com.dbfleetops.operation.domain.OperationTaskType;
import com.dbfleetops.operation.dto.NextOperationTaskResponse;
import com.dbfleetops.operation.domain.TaskExecutionConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentOperationTaskController.class)
class AgentOperationTaskControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AgentTasks taskService;
    @MockitoBean TaskReports taskReports;
    @MockitoBean TaskClaim claimService;
    @MockitoBean TaskLease leaseService;
    @MockitoBean TaskCredential credentialService;

    @Test
    void postNextReturnsClaimedExecution() throws Exception {
        when(claimService.claimNext(1L, "token")).thenReturn(new NextOperationTaskResponse(
                true, 10L, OperationTaskType.COLLECT_LINUX_STATUS, "{}", 1,
                LocalDateTime.of(2026, 8, 7, 12, 1)));

        mockMvc.perform(post("/internal/v1/agents/1/tasks/next")
                        .queryParam("agentToken", "token").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(10))
                .andExpect(jsonPath("$.executionAttempt").value(1))
                .andExpect(jsonPath("$.leaseExpiresAt").exists());
    }

    @Test
    void missingExecutionAttemptIsRejected() throws Exception {
        mockMvc.perform(post("/internal/v1/agents/1/tasks/10/lease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentToken\":\"token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DBOPS-COMMON-40002"));
    }

    @Test
    void staleExecutionReturnsStableConflict() throws Exception {
        when(leaseService.renew(any(), any(), any()))
                .thenThrow(new TaskExecutionConflictException("stale execution"));

        mockMvc.perform(post("/internal/v1/agents/1/tasks/10/lease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentToken\":\"token\",\"executionAttempt\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DBOPS-TASK-40901"));
    }

    @Test
    void missingResultReportIdIsRejected() throws Exception {
        mockMvc.perform(post("/internal/v1/agents/1/tasks/10/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentToken":"token","executionAttempt":1,"resultPayloadJson":"{}"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DBOPS-COMMON-40002"));
    }

    @Test
    void malformedResultReportIdIsRejected() throws Exception {
        mockMvc.perform(post("/internal/v1/agents/1/tasks/10/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentToken":"token","executionAttempt":1,
                                 "resultReportId":"not-a-uuid","errorCode":"FAILED","errorMessage":"error"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DBOPS-COMMON-40002"));
    }
}
