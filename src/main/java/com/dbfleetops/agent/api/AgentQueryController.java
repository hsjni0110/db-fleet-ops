package com.dbfleetops.agent.api;

import com.dbfleetops.agent.application.AgentQueryService;
import com.dbfleetops.agent.dto.AgentConsoleResponse;
import com.dbfleetops.agent.dto.AgentDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentQueryController {

    private final AgentQueryService agentQueryService;

    public AgentQueryController(AgentQueryService agentQueryService) {
        this.agentQueryService = agentQueryService;
    }

    @GetMapping
    public List<AgentConsoleResponse> findAll() {
        return agentQueryService.findAll();
    }

    @GetMapping("/{agentId}")
    public AgentDetailResponse findById(@PathVariable Long agentId) {
        return agentQueryService.findById(agentId);
    }
}
