package com.dbfleetops.operation.api;

import com.dbfleetops.operation.application.OperationTaskService;
import com.dbfleetops.operation.application.TaskCredentialService;
import com.dbfleetops.operation.application.OperationTaskClaimService;
import com.dbfleetops.operation.application.OperationTaskLeaseService;
import com.dbfleetops.operation.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/v1/agents")
public class AgentOperationTaskController {

    private final OperationTaskService operationTaskService;
    private final OperationTaskClaimService operationTaskClaimService;
    private final OperationTaskLeaseService operationTaskLeaseService;
    private final TaskCredentialService taskCredentialService;

    public AgentOperationTaskController(OperationTaskService operationTaskService,
            OperationTaskClaimService operationTaskClaimService,
            OperationTaskLeaseService operationTaskLeaseService,
            TaskCredentialService taskCredentialService) {
        this.operationTaskService = operationTaskService;
        this.operationTaskClaimService = operationTaskClaimService;
        this.operationTaskLeaseService = operationTaskLeaseService;
        this.taskCredentialService = taskCredentialService;
    }

    @PostMapping("/{agentId}/tasks/{taskId}/credential")
    public ResponseEntity<com.dbfleetops.operation.dto.TaskCredentialResponse> credential(
            @PathVariable Long agentId, @PathVariable Long taskId,
            @Valid @RequestBody com.dbfleetops.operation.dto.ResolveTaskCredentialRequest request) {
        return ResponseEntity.ok(taskCredentialService.resolve(agentId, taskId, request));
    }

    @PostMapping("/tasks")
    public ResponseEntity<OperationTaskResponse> createTask(
            @RequestBody CreateOperationTaskRequest request) {
        return ResponseEntity.ok(operationTaskService.createTask(request));
    }

    @PostMapping("/{agentId}/tasks/next")
    public ResponseEntity<NextOperationTaskResponse> nextTask(@PathVariable Long agentId,
            @RequestParam String agentToken) {
        return ResponseEntity.ok(operationTaskClaimService.claimNext(agentId, agentToken));
    }

    @PostMapping("/{agentId}/tasks/{taskId}/lease")
    public ResponseEntity<OperationTaskLeaseResponse> renewLease(@PathVariable Long agentId,
            @PathVariable Long taskId, @Valid @RequestBody RenewOperationTaskLeaseRequest request) {
        return ResponseEntity.ok(operationTaskLeaseService.renew(agentId, taskId, request));
    }

    @PostMapping("/{agentId}/tasks/{taskId}/complete")
    public ResponseEntity<OperationTaskResponse> completeTask(@PathVariable Long agentId,
            @PathVariable Long taskId, @Valid @RequestBody CompleteOperationTaskRequest request) {
        return ResponseEntity.ok(operationTaskService.completeTask(agentId, taskId, request));
    }

    @PostMapping("/{agentId}/tasks/{taskId}/fail")
    public ResponseEntity<OperationTaskResponse> failTask(@PathVariable Long agentId,
            @PathVariable Long taskId, @Valid @RequestBody FailOperationTaskRequest request) {
        return ResponseEntity.ok(operationTaskService.failTask(agentId, taskId, request));
    }
}
