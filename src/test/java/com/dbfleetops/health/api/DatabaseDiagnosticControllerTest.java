package com.dbfleetops.health.api;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.application.DatabaseDiagnosticService;
import com.dbfleetops.health.dto.DatabaseUptimeResponse;
import com.dbfleetops.health.dto.DatabaseVersionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
}