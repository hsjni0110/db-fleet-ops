package com.dbfleetops.health.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.dbfleetops.DbFleetopsApplication;
import com.dbfleetops.health.domain.DatabaseHealth;
import com.dbfleetops.health.port.DatabaseHealthProbe;

@SpringBootTest(
        classes = DbFleetopsApplication.class,
        properties = {
                "db-fleetops.target.host=127.0.0.1",
                "db-fleetops.target.port=3306",
                "db-fleetops.target.database=dbops_target",
                "db-fleetops.target.username=db_monitor",
                "db-fleetops.target.password=test_password",
                "db-fleetops.target.connect-timeout=2s",
                "db-fleetops.target.socket-timeout=2s"
        }
)
@AutoConfigureMockMvc
class DatabaseHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseHealthProbe databaseHealthProbe;

    @Test
    void returnsDatabaseHealth() throws Exception {
        DatabaseHealth health = DatabaseHealth.up(
                "MYSQL",
                "127.0.0.1",
                3306,
                14L,
                OffsetDateTime.parse(
                        "2026-06-29T17:30:00+09:00"
                )
        );

        when(databaseHealthProbe.check())
                .thenReturn(health);

        mockMvc.perform(
                        get("/api/v1/databases/default/health")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.databaseType")
                                .value("MYSQL")
                )
                .andExpect(
                        jsonPath("$.host")
                                .value("127.0.0.1")
                )
                .andExpect(
                        jsonPath("$.port")
                                .value(3306)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("UP")
                )
                .andExpect(
                        jsonPath("$.latencyMs")
                                .value(14)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(nullValue())
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Database connection is available."
                                )
                );
    }
    
    @Test
    void returnsDownWhenDatabaseIsUnavailable() throws Exception {
        DatabaseHealth health = DatabaseHealth.down(
                "MYSQL",
                "127.0.0.1",
                3306,
                1002L,
                OffsetDateTime.parse(
                        "2026-06-29T17:31:00+09:00"
                ),
                com.dbfleetops.health.domain.DatabaseErrorCode
                        .CONNECTION_REFUSED,
                "Database connection was refused."
        );

        when(databaseHealthProbe.check())
                .thenReturn(health);

        mockMvc.perform(
                        get("/api/v1/databases/default/health")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("DOWN")
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("CONNECTION_REFUSED")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Database connection was refused."
                                )
                );
    }
}