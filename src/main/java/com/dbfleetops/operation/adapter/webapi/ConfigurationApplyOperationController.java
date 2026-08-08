package com.dbfleetops.operation.adapter.webapi;

import com.dbfleetops.operation.application.provided.JobOperations;
import com.dbfleetops.operation.dto.CreateConfigurationApplyJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/database-instances/{databaseId}/operations/configuration-applies")
public class ConfigurationApplyOperationController {

    private final JobOperations operationJobService;

    public ConfigurationApplyOperationController(JobOperations operationJobService) {
        this.operationJobService = operationJobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OperationJobResponse createConfigurationApplyJob(@PathVariable Long databaseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateConfigurationApplyJobRequest request) {
        return operationJobService.createConfigurationApplyJob(databaseId, idempotencyKey, request);
    }
}
