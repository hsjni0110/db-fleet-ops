package com.dbfleetops.operation.api;

import com.dbfleetops.operation.application.OperationWorkerService;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.dto.OperationJobResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OperationWorkerController.class)
class OperationWorkerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperationWorkerService workerService;

    @Test
    void claimJobReturnsClaimedJob() throws Exception {
        when(workerService.claimJob("worker-1"))
                .thenReturn(new ClaimJobResponse(
                        true,
                        1L,
                        JobType.BACKUP,
                        JobStatus.RUNNING,
                        1L,
                        "worker-1",
                        LocalDateTime.of(2026, 7, 2, 16, 0)
                ));

        mockMvc.perform(post(
                        "/internal/v1/workers/worker-1/jobs/claim"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true))
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.jobType").value("BACKUP"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.targetDatabaseId").value(1))
                .andExpect(jsonPath("$.leaseOwner").value("worker-1"));
    }

    @Test
    void claimJobReturnsEmptyWhenNoJobExists() throws Exception {
        when(workerService.claimJob("worker-1"))
                .thenReturn(ClaimJobResponse.empty());

        mockMvc.perform(post(
                        "/internal/v1/workers/worker-1/jobs/claim"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(false))
                .andExpect(jsonPath("$.jobId").doesNotExist());
    }

    @Test
    void succeedJobReturnsSucceededJob() throws Exception {
        when(workerService.succeedJob(
                org.mockito.ArgumentMatchers.eq("worker-1"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new OperationJobResponse(
                1L,
                JobType.BACKUP,
                JobStatus.SUCCEEDED,
                1L,
                "local-user",
                0,
                3,
                "worker-1",
                LocalDateTime.of(2026, 7, 2, 16, 0),
                LocalDateTime.of(2026, 7, 2, 15, 59),
                LocalDateTime.of(2026, 7, 2, 15, 59),
                LocalDateTime.of(2026, 7, 2, 16, 0),
                "SUCCESS",
                "backup completed",
                LocalDateTime.of(2026, 7, 2, 15, 58)
        ));

        String body = """
                {
                "resultMessage": "backup completed"
                }
                """;

        mockMvc.perform(post(
                        "/internal/v1/workers/worker-1/jobs/1/succeed"
                )
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultCode").value("SUCCESS"))
                .andExpect(jsonPath("$.resultMessage").value("backup completed"));
    }

    @Test
    void failJobReturnsFailedJob() throws Exception {
        when(workerService.failJob(
                org.mockito.ArgumentMatchers.eq("worker-1"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new OperationJobResponse(
                1L,
                JobType.BACKUP,
                JobStatus.FAILED,
                1L,
                "local-user",
                0,
                3,
                "worker-1",
                LocalDateTime.of(2026, 7, 2, 16, 0),
                LocalDateTime.of(2026, 7, 2, 15, 59),
                LocalDateTime.of(2026, 7, 2, 15, 59),
                LocalDateTime.of(2026, 7, 2, 16, 0),
                "BACKUP_FAILED",
                "mysqldump failed",
                LocalDateTime.of(2026, 7, 2, 15, 58)
        ));

        String body = """
                {
                "resultCode": "BACKUP_FAILED",
                "resultMessage": "mysqldump failed"
                }
                """;

        mockMvc.perform(post(
                        "/internal/v1/workers/worker-1/jobs/1/fail"
                )
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.resultCode").value("BACKUP_FAILED"))
                .andExpect(jsonPath("$.resultMessage").value("mysqldump failed"));
    }
}