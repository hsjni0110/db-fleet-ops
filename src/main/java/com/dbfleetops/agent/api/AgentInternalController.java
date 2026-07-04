package com.dbfleetops.agent.api;

import com.dbfleetops.agent.application.AgentService;
import com.dbfleetops.agent.dto.AgentHeartbeatRequest;
import com.dbfleetops.agent.dto.AgentHeartbeatResponse;
import com.dbfleetops.agent.dto.RegisterAgentRequest;
import com.dbfleetops.agent.dto.RegisterAgentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/agents")
public class AgentInternalController {

    private final AgentService agentService;

    public AgentInternalController(
            AgentService agentService
    ) {
        this.agentService = agentService;
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
}