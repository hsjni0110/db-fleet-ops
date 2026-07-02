package com.dbfleetops.operation.api;

import com.dbfleetops.operation.application.OperationWorkerService;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/workers/{workerId}/jobs")
public class OperationWorkerController {

    private final OperationWorkerService workerService;

    public OperationWorkerController(
            OperationWorkerService workerService
    ) {
        this.workerService = workerService;
    }

    @PostMapping("/claim")
    public ResponseEntity<ClaimJobResponse> claimJob(
            @PathVariable String workerId
    ) {
        return ResponseEntity.ok(
                workerService.claimJob(workerId)
        );
    }
}