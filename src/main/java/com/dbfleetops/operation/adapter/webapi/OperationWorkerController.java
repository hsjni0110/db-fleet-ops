package com.dbfleetops.operation.adapter.webapi;

import com.dbfleetops.operation.application.provided.WorkerJobs;
import com.dbfleetops.operation.dto.ClaimJobResponse;
import com.dbfleetops.operation.dto.FailJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import com.dbfleetops.operation.dto.SucceedJobRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/workers/{workerId}/jobs")
public class OperationWorkerController {

    private final WorkerJobs workerService;

    public OperationWorkerController(
            WorkerJobs workerService
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
    
    @PostMapping("/{jobId}/succeed")
    public ResponseEntity<OperationJobResponse> succeedJob(
            @PathVariable String workerId,
            @PathVariable Long jobId,
            @RequestBody SucceedJobRequest request
    ) {
        return ResponseEntity.ok(
                workerService.succeedJob(
                        workerId,
                        jobId,
                        request
                )
        );
    }

    @PostMapping("/{jobId}/fail")
    public ResponseEntity<OperationJobResponse> failJob(
            @PathVariable String workerId,
            @PathVariable Long jobId,
            @RequestBody FailJobRequest request
    ) {
        return ResponseEntity.ok(
                workerService.failJob(
                        workerId,
                        jobId,
                        request
                )
        );
    }
}
