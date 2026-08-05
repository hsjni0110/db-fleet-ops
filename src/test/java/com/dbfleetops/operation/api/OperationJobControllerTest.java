package com.dbfleetops.operation.api;

import com.dbfleetops.operation.application.OperationJobService;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.dto.OperationJobResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OperationJobController.class)
class OperationJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperationJobService operationJobService;

    @Test
    void createBackupJobReturnsAcceptedJob() throws Exception {
        OperationJobResponse response =
                new OperationJobResponse(
                        1L,
                        JobType.BACKUP,
                        JobStatus.QUEUED,
                        1L,
                        "local-user",
                        0,
                        3,
                        null,
                        null,
                        LocalDateTime.of(2026, 7, 1, 16, 0),
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.of(2026, 7, 1, 16, 0)
                );

        when(operationJobService.createBackupJob(
                eq(1L),
                eq("idem-001"),
                any()
        )).thenReturn(response);

        String body = """
                {
                  "reason": "manual backup test",
                  "requestedBy": "local-user"
                }
                """;

        mockMvc.perform(post(
                        "/api/v1/database-instances/1/operations/backups"
                )
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.jobType").value("BACKUP"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.targetDatabaseId").value(1))
                .andExpect(jsonPath("$.requestedBy").value("local-user"));
    }

    @Test
    void getJobReturnsJobResponse() throws Exception {
        OperationJobResponse response =
                new OperationJobResponse(
                        1L,
                        JobType.BACKUP,
                        JobStatus.QUEUED,
                        1L,
                        "local-user",
                        0,
                        3,
                        null,
                        null,
                        LocalDateTime.of(2026, 7, 1, 16, 0),
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.of(2026, 7, 1, 16, 0)
                );

        when(operationJobService.getJob(1L))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.jobType").value("BACKUP"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void getJobsReturnsJobsInServiceOrder() throws Exception {
        OperationJobResponse newest = new OperationJobResponse(
                2L, JobType.BACKUP, JobStatus.RUNNING, 1L, "operator", 0, 3,
                null, null, LocalDateTime.of(2026, 7, 2, 10, 0),
                LocalDateTime.of(2026, 7, 2, 10, 1), null, null, null,
                LocalDateTime.of(2026, 7, 2, 10, 0));
        OperationJobResponse older = new OperationJobResponse(
                1L, JobType.BACKUP, JobStatus.SUCCEEDED, 1L, "operator", 0, 3,
                null, null, LocalDateTime.of(2026, 7, 1, 10, 0),
                LocalDateTime.of(2026, 7, 1, 10, 1),
                LocalDateTime.of(2026, 7, 1, 10, 2), "SUCCESS", "done",
                LocalDateTime.of(2026, 7, 1, 10, 0));

        when(operationJobService.getJobs()).thenReturn(List.of(newest, older));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(2))
                .andExpect(jsonPath("$[0].status").value("RUNNING"))
                .andExpect(jsonPath("$[1].jobId").value(1));
    }

    @Test
    void getJobsReturnsEmptyArray() throws Exception {
        when(operationJobService.getJobs()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
