package com.dbfleetops.health.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbfleetops.health.application.DatabaseHealthService;
import com.dbfleetops.health.domain.DatabaseHealth;

@RestController
@RequestMapping("/api/v1/databases/default")
public class DatabaseHealthController {

    private final DatabaseHealthService databaseHealthService;

    public DatabaseHealthController(
            DatabaseHealthService databaseHealthService
    ) {
        this.databaseHealthService = databaseHealthService;
    }

    @GetMapping("/health")
    public ResponseEntity<DatabaseHealthResponse> getHealth() {
        DatabaseHealth health =
                databaseHealthService.checkDefaultDatabase();

        DatabaseHealthResponse response =
                DatabaseHealthResponse.from(health);

        return ResponseEntity.ok(response);
    }
}