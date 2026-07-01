package com.dbfleetops.health.api;

import com.dbfleetops.health.application.DatabaseDiagnosticService;
import com.dbfleetops.health.dto.DatabaseUptimeResponse;
import com.dbfleetops.health.dto.DatabaseVersionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/database-instances/{databaseId}/diagnostics")
public class DatabaseDiagnosticController {

    private final DatabaseDiagnosticService diagnosticService;

    public DatabaseDiagnosticController(
            DatabaseDiagnosticService diagnosticService
    ) {
        this.diagnosticService = diagnosticService;
    }

    @GetMapping("/version")
    public ResponseEntity<DatabaseVersionResponse> getVersion(
            @PathVariable Long databaseId
    ) {
        return ResponseEntity.ok(
                diagnosticService.getVersion(databaseId)
        );
    }

    @GetMapping("/uptime")
    public ResponseEntity<DatabaseUptimeResponse> getUptime(
            @PathVariable Long databaseId
    ) {
        return ResponseEntity.ok(
                diagnosticService.getUptime(databaseId)
        );
    }
}