package com.dbfleetops.operation.api;

import com.dbfleetops.operation.application.OperationTaskService;
import com.dbfleetops.operation.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/agents")
public class AgentOperationTaskController {

    private final OperationTaskService operationTaskService;

    public AgentOperationTaskController(OperationTaskService operationTaskService) {
        this.operationTaskService = operationTaskService;
    }

    @PostMapping("/tasks")
    public ResponseEntity<OperationTaskResponse> createTask(
            @RequestBody CreateOperationTaskRequest request) {
        return ResponseEntity.ok(operationTaskService.createTask(request));
    }

    @GetMapping("/{agentId}/tasks/next")
    public ResponseEntity<NextOperationTaskResponse> nextTask(@PathVariable Long agentId,
            @RequestParam String agentToken) {
        return ResponseEntity.ok(operationTaskService.nextTask(agentId, agentToken));
    }

    @PostMapping("/{agentId}/tasks/{taskId}/start")
    public ResponseEntity<OperationTaskResponse> startTask(@PathVariable Long agentId,
            @PathVariable Long taskId, @RequestBody StartOperationTaskRequest request) {
        return ResponseEntity.ok(operationTaskService.startTask(agentId, taskId, request));
    }

    @PostMapping("/{agentId}/tasks/{taskId}/complete")
    public ResponseEntity<OperationTaskResponse> completeTask(@PathVariable Long agentId,
            @PathVariable Long taskId, @RequestBody CompleteOperationTaskRequest request) {
        return ResponseEntity.ok(operationTaskService.completeTask(agentId, taskId, request));
    }

    @PostMapping("/{agentId}/tasks/{taskId}/fail")
    public ResponseEntity<OperationTaskResponse> failTask(@PathVariable Long agentId,
            @PathVariable Long taskId, @RequestBody FailOperationTaskRequest request) {
        return ResponseEntity.ok(operationTaskService.failTask(agentId, taskId, request));
    }
}
