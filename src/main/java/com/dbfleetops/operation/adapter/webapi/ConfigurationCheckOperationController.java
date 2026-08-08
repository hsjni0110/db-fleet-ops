package com.dbfleetops.operation.adapter.webapi;

import com.dbfleetops.operation.application.provided.JobOperations;
import com.dbfleetops.operation.dto.CreateConfigurationCheckJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
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
