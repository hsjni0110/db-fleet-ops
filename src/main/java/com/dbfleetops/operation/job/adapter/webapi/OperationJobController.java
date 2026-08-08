package com.dbfleetops.operation.job.adapter.webapi;

import com.dbfleetops.operation.job.application.provided.JobOperations;
import com.dbfleetops.operation.job.dto.CreateBackupJobRequest;
import com.dbfleetops.operation.job.dto.OperationJobResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class OperationJobController {

    private final JobOperations operationJobService;

    public OperationJobController(
            JobOperations operationJobService
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

    @GetMapping("/api/v1/jobs")
    public ResponseEntity<List<OperationJobResponse>> getJobs() {
        return ResponseEntity.ok(operationJobService.getJobs());
    }
}
