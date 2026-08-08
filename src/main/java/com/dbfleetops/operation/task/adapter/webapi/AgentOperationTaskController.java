package com.dbfleetops.operation.task.adapter.webapi;

import com.dbfleetops.operation.task.application.provided.AgentTasks;
import com.dbfleetops.operation.task.application.provided.TaskClaim;
import com.dbfleetops.operation.task.application.provided.TaskCredential;
import com.dbfleetops.operation.task.application.provided.TaskLease;
import com.dbfleetops.operation.task.application.provided.TaskReports;
import com.dbfleetops.operation.task.dto.CompleteOperationTaskRequest;
import com.dbfleetops.operation.task.dto.CreateOperationTaskRequest;
import com.dbfleetops.operation.task.dto.FailOperationTaskRequest;
import com.dbfleetops.operation.task.dto.NextOperationTaskResponse;
import com.dbfleetops.operation.task.dto.OperationTaskLeaseResponse;
import com.dbfleetops.operation.task.dto.OperationTaskResponse;
import com.dbfleetops.operation.task.dto.RenewOperationTaskLeaseRequest;
import com.dbfleetops.operation.task.dto.ResolveTaskCredentialRequest;
import com.dbfleetops.operation.task.dto.TaskCredentialResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/v1/agents")
public class AgentOperationTaskController {

    private final AgentTasks operationTaskService;
    private final TaskReports taskReportService;
    private final TaskClaim operationTaskClaimService;
    private final TaskLease operationTaskLeaseService;
    private final TaskCredential taskCredentialService;

    public AgentOperationTaskController(AgentTasks operationTaskService, TaskReports taskReportService,
            TaskClaim operationTaskClaimService,
            TaskLease operationTaskLeaseService,
            TaskCredential taskCredentialService) {
        this.operationTaskService = operationTaskService;
        this.taskReportService = taskReportService;
        this.operationTaskClaimService = operationTaskClaimService;
        this.operationTaskLeaseService = operationTaskLeaseService;
        this.taskCredentialService = taskCredentialService;
    }

    @PostMapping("/{agentId}/tasks/{taskId}/credential")
    public ResponseEntity<com.dbfleetops.operation.task.dto.TaskCredentialResponse> credential(
            @PathVariable Long agentId, @PathVariable Long taskId,
            @Valid @RequestBody com.dbfleetops.operation.task.dto.ResolveTaskCredentialRequest request) {
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
        return ResponseEntity.ok(taskReportService.completeTask(agentId, taskId, request));
    }

    @PostMapping("/{agentId}/tasks/{taskId}/fail")
    public ResponseEntity<OperationTaskResponse> failTask(@PathVariable Long agentId,
            @PathVariable Long taskId, @Valid @RequestBody FailOperationTaskRequest request) {
        return ResponseEntity.ok(taskReportService.failTask(agentId, taskId, request));
    }
}
