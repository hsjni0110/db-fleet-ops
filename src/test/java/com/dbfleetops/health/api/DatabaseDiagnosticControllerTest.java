package com.dbfleetops.health.api;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.application.DatabaseDiagnosticService;
import com.dbfleetops.health.dto.ConnectionSummaryResponse;
import com.dbfleetops.health.dto.DatabaseUptimeResponse;
import com.dbfleetops.health.dto.DatabaseVersionResponse;
import com.dbfleetops.health.dto.LockWaitResponse;
import com.dbfleetops.health.dto.LongTransactionResponse;
import com.dbfleetops.health.dto.SessionResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

@WebMvcTest(DatabaseDiagnosticController.class)
class DatabaseDiagnosticControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseDiagnosticService diagnosticService;

    @Test
    void getVersionReturnsVersionResponse() throws Exception {
        when(diagnosticService.getVersion(1L))
                .thenReturn(new DatabaseVersionResponse(
                        1L,
                        DatabaseEngine.MYSQL,
                        "8.4.0"
                ));

        mockMvc.perform(get(
                        "/api/v1/database-instances/1/diagnostics/version"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseId").value(1))
                .andExpect(jsonPath("$.engine").value("MYSQL"))
                .andExpect(jsonPath("$.version").value("8.4.0"));
    }

    @Test
    void getUptimeReturnsUptimeResponse() throws Exception {
        when(diagnosticService.getUptime(1L))
                .thenReturn(new DatabaseUptimeResponse(
                        1L,
                        DatabaseEngine.MYSQL,
                        3600L
                ));

        mockMvc.perform(get(
                        "/api/v1/database-instances/1/diagnostics/uptime"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseId").value(1))
                .andExpect(jsonPath("$.engine").value("MYSQL"))
                .andExpect(jsonPath("$.uptimeSeconds").value(3600));
    }

    @Test
    void getConnectionSummaryReturnsConnectionSummaryResponse() throws Exception {
        when(diagnosticService.getConnectionSummary(1L))
                .thenReturn(new ConnectionSummaryResponse(
                        1L,
                        DatabaseEngine.MYSQL,
                        12,
                        2,
                        151,
                        7.95
                ));

        mockMvc.perform(get(
                        "/api/v1/database-instances/1/diagnostics/connections"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseId").value(1))
                .andExpect(jsonPath("$.engine").value("MYSQL"))
                .andExpect(jsonPath("$.currentConnections").value(12))
                .andExpect(jsonPath("$.runningConnections").value(2))
                .andExpect(jsonPath("$.maxConnections").value(151))
                .andExpect(jsonPath("$.usagePercent").value(7.95));
    }

    @Test
    void getSessionsReturnsSessionResponses() throws Exception {
        when(diagnosticService.getSessions(1L))
                .thenReturn(List.of(
                        new SessionResponse(
                                1L,
                                DatabaseEngine.MYSQL,
                                10L,
                                "db_monitor",
                                "localhost:50000",
                                "orders",
                                "Query",
                                3L,
                                "executing",
                                "SELECT * FROM orders"
                        )
                ));

        mockMvc.perform(get(
                        "/api/v1/database-instances/1/diagnostics/sessions"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].databaseId").value(1))
                .andExpect(jsonPath("$[0].engine").value("MYSQL"))
                .andExpect(jsonPath("$[0].processId").value(10))
                .andExpect(jsonPath("$[0].user").value("db_monitor"))
                .andExpect(jsonPath("$[0].command").value("Query"))
                .andExpect(jsonPath("$[0].queryPreview")
                        .value("SELECT * FROM orders"));
    }

    @Test
    void getLongTransactionsReturnsLongTransactionResponses() throws Exception {
        when(diagnosticService.getLongTransactions(1L))
                .thenReturn(List.of(
                        new LongTransactionResponse(
                                1L,
                                DatabaseEngine.MYSQL,
                                "12345",
                                "RUNNING",
                                LocalDateTime.of(2026, 7, 1, 15, 30),
                                120L,
                                10L,
                                "UPDATE orders SET status = 'PAID'"
                        )
                ));

        mockMvc.perform(get(
                        "/api/v1/database-instances/1/diagnostics/long-transactions"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].databaseId").value(1))
                .andExpect(jsonPath("$[0].engine").value("MYSQL"))
                .andExpect(jsonPath("$[0].transactionId").value("12345"))
                .andExpect(jsonPath("$[0].state").value("RUNNING"))
                .andExpect(jsonPath("$[0].durationSeconds").value(120))
                .andExpect(jsonPath("$[0].threadId").value(10));
    }

    @Test
    void getLockWaitsReturnsLockWaitResponses() throws Exception {
        when(diagnosticService.getLockWaits(1L))
                .thenReturn(List.of(
                        new LockWaitResponse(
                                1L,
                                DatabaseEngine.MYSQL,
                                "waiting-1",
                                11L,
                                "UPDATE orders SET status = 'PAID'",
                                "blocking-1",
                                10L,
                                "SELECT * FROM orders WHERE id = 1"
                        )
                ));

        mockMvc.perform(get(
                        "/api/v1/database-instances/1/diagnostics/lock-waits"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].databaseId").value(1))
                .andExpect(jsonPath("$[0].engine").value("MYSQL"))
                .andExpect(jsonPath("$[0].waitingTransactionId").value("waiting-1"))
                .andExpect(jsonPath("$[0].waitingThreadId").value(11))
                .andExpect(jsonPath("$[0].blockingTransactionId").value("blocking-1"))
                .andExpect(jsonPath("$[0].blockingThreadId").value(10));
    }
}