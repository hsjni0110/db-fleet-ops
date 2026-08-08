package com.dbfleetops.operation.job.adapter.webapi;

import com.dbfleetops.operation.job.application.provided.JobOperations;
import com.dbfleetops.operation.job.dto.CreateConfigurationCheckJobRequest;
import com.dbfleetops.operation.job.dto.OperationJobResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/database-instances/{databaseId}/operations/configuration-checks")
public class ConfigurationCheckOperationController {

    private final JobOperations operationJobService;

    public ConfigurationCheckOperationController(JobOperations operationJobService) {
        this.operationJobService = operationJobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OperationJobResponse createConfigurationCheckJob(@PathVariable Long databaseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateConfigurationCheckJobRequest request) {
        return operationJobService.createConfigurationCheckJob(databaseId, idempotencyKey, request);
    }
}
