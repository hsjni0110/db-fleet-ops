package com.dbfleetops.operation.api;

import com.dbfleetops.operation.application.OperationJobService;
import com.dbfleetops.operation.dto.CreateConfigurationCheckJobRequest;
import com.dbfleetops.operation.dto.OperationJobResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/database-instances/{databaseId}/operations/configuration-checks")
public class ConfigurationCheckOperationController {

    private final OperationJobService operationJobService;

    public ConfigurationCheckOperationController(OperationJobService operationJobService) {
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
