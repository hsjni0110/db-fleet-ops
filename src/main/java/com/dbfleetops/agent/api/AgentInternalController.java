package com.dbfleetops.agent.api;

import com.dbfleetops.agent.application.AgentService;
import com.dbfleetops.agent.application.AgentTaskService;
import com.dbfleetops.agent.dto.AgentHeartbeatRequest;
import com.dbfleetops.agent.dto.AgentHeartbeatResponse;
import com.dbfleetops.agent.dto.AgentTaskResponse;
import com.dbfleetops.agent.dto.CompleteAgentTaskRequest;
import com.dbfleetops.agent.dto.CreateAgentTaskRequest;
import com.dbfleetops.agent.dto.FailAgentTaskRequest;
import com.dbfleetops.agent.dto.NextAgentTaskResponse;
import com.dbfleetops.agent.dto.RegisterAgentRequest;
import com.dbfleetops.agent.dto.RegisterAgentResponse;
import com.dbfleetops.agent.dto.StartAgentTaskRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/agents")
public class AgentInternalController {

    private final AgentService agentService;
    private final AgentTaskService agentTaskService;

    public AgentInternalController(
            AgentService agentService,
            AgentTaskService agentTaskService
    ) {
        this.agentService = agentService;
        this.agentTaskService = agentTaskService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterAgentResponse> register(
            @RequestBody RegisterAgentRequest request
    ) {
        return ResponseEntity.ok(
                agentService.register(request)
        );
    }

    @PostMapping("/{agentId}/heartbeats")
    public ResponseEntity<AgentHeartbeatResponse> heartbeat(
            @PathVariable Long agentId,
            @RequestBody AgentHeartbeatRequest request
    ) {
        return ResponseEntity.ok(
                agentService.heartbeat(
                        agentId,
                        request
                )
        );
    }
    @PostMapping("/tasks")
    public ResponseEntity<AgentTaskResponse> createTask(
            @RequestBody CreateAgentTaskRequest request
    ) {
        return ResponseEntity.ok(
                agentTaskService.createTask(request)
        );
    }

    @GetMapping("/{agentId}/tasks/next")
    public ResponseEntity<NextAgentTaskResponse> nextTask(
            @PathVariable Long agentId,
            @RequestParam String agentToken
    ) {
        return ResponseEntity.ok(
                agentTaskService.nextTask(
                        agentId,
                        agentToken
                )
        );
    }

    @PostMapping("/{agentId}/tasks/{taskId}/start")
    public ResponseEntity<AgentTaskResponse> startTask(
            @PathVariable Long agentId,
            @PathVariable Long taskId,
            @RequestBody StartAgentTaskRequest request
    ) {
        return ResponseEntity.ok(
                agentTaskService.startTask(
                        agentId,
                        taskId,
                        request
                )
        );
    }

    @PostMapping("/{agentId}/tasks/{taskId}/complete")
    public ResponseEntity<AgentTaskResponse> completeTask(
            @PathVariable Long agentId,
            @PathVariable Long taskId,
            @RequestBody CompleteAgentTaskRequest request
    ) {
        return ResponseEntity.ok(
                agentTaskService.completeTask(
                        agentId,
                        taskId,
                        request
                )
        );
    }

    @PostMapping("/{agentId}/tasks/{taskId}/fail")
    public ResponseEntity<AgentTaskResponse> failTask(
            @PathVariable Long agentId,
            @PathVariable Long taskId,
            @RequestBody FailAgentTaskRequest request
    ) {
        return ResponseEntity.ok(
                agentTaskService.failTask(
                        agentId,
                        taskId,
                        request
                )
        );
    }
}