package com.dbfleetops.operation.api;

import com.dbfleetops.operation.application.OperationJobService;
import com.dbfleetops.operation.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OperationJobController {

    private final OperationJobService operationJobService;

    public OperationJobController(
            OperationJobService operationJobService
    ) {
        this.operationJobService = operationJobService;
    }

    @PostMapping("/api/v1/database-instances/{databaseId}/operations/backups")
    public ResponseEntity<OperationJobResponse> createBackupJob(
            @PathVariable Long databaseId,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey,
            @RequestBody CreateBackupJobRequest request
    ) {
        OperationJobResponse response =
                operationJobService.createBackupJob(
                        databaseId,
                        idempotencyKey,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }

    @GetMapping("/api/v1/jobs/{jobId}")
    public ResponseEntity<OperationJobResponse> getJob(
            @PathVariable Long jobId
    ) {
        return ResponseEntity.ok(
                operationJobService.getJob(jobId)
        );
    }
}