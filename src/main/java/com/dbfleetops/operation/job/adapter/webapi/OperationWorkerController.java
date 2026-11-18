package com.dbfleetops.operation.job.adapter.webapi;

import com.dbfleetops.operation.job.application.provided.JobAcquisition;
import com.dbfleetops.operation.job.application.provided.JobReports;
import com.dbfleetops.operation.job.dto.JobAcquisitionResponse;
import com.dbfleetops.operation.job.dto.FailJobRequest;
import com.dbfleetops.operation.job.dto.OperationJobResponse;
import com.dbfleetops.operation.job.dto.SucceedJobRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/workers/{workerId}/jobs")
public class OperationWorkerController {

    private final JobAcquisition jobAcquisition;
    private final JobReports jobReports;

    public OperationWorkerController(JobAcquisition jobAcquisition, JobReports jobReports) {
        this.jobAcquisition = jobAcquisition;
        this.jobReports = jobReports;
    }

    @PostMapping("/acquire")
    public ResponseEntity<JobAcquisitionResponse> acquireJob(
            @PathVariable String workerId
    ) {
        return ResponseEntity.ok(
                jobAcquisition.acquireJob(workerId)
        );
    }
    
    @PostMapping("/{jobId}/succeed")
    public ResponseEntity<OperationJobResponse> succeedJob(
            @PathVariable String workerId,
            @PathVariable Long jobId,
            @RequestBody SucceedJobRequest request
    ) {
        return ResponseEntity.ok(
                jobReports.succeedJob(
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
                jobReports.failJob(
                        workerId,
                        jobId,
                        request
                )
        );
    }
}
