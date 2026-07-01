package com.dbfleetops.health.api;

import com.dbfleetops.health.application.DatabaseHealthService;
import com.dbfleetops.health.domain.DatabaseHealth;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class DatabaseHealthController {

    private final DatabaseHealthService databaseHealthService;

    public DatabaseHealthController(
            DatabaseHealthService databaseHealthService
    ) {
        this.databaseHealthService = databaseHealthService;
    }

    @GetMapping("/api/v1/databases/default/health")
    public ResponseEntity<DatabaseHealthResponse> getHealth() {
        DatabaseHealth health =
                databaseHealthService.checkDefaultDatabase();

        DatabaseHealthResponse response =
                DatabaseHealthResponse.from(health);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/database-instances/{databaseId}/health-checks")
    public ResponseEntity<com.dbfleetops.health.dto.DatabaseHealthResponse> checkDatabaseInstance(
            @PathVariable Long databaseId
    ) {
        com.dbfleetops.health.dto.DatabaseHealthResponse response =
                databaseHealthService.check(databaseId);

        return ResponseEntity.ok(response);
    }
}