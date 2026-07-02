package com.dbfleetops.operation.api;

import com.dbfleetops.operation.application.OperationWorkerService;
import com.dbfleetops.operation.domain.JobStatus;
import com.dbfleetops.operation.domain.JobType;
import com.dbfleetops.operation.dto.ClaimJobResponse;
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
}