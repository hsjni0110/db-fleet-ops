package com.dbfleetops.policy.api;

import com.dbfleetops.policy.application.ConfigurationDriftService;
import com.dbfleetops.policy.dto.ConfigurationDriftResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ConfigurationDriftController {

    private final ConfigurationDriftService driftService;

    public ConfigurationDriftController(ConfigurationDriftService driftService) {
        this.driftService = driftService;
    }

    @GetMapping("/database-instances/{databaseId}/configuration-drifts/latest")
    public ConfigurationDriftResponse getLatestDrift(@PathVariable Long databaseId) {
        return driftService.getLatestDrift(databaseId);
    }

    @GetMapping("/database-instances/{databaseId}/configuration-drifts")
    public List<ConfigurationDriftResponse> getDriftsByDatabaseId(@PathVariable Long databaseId) {
        return driftService.getDriftsByDatabaseId(databaseId);
    }

    @GetMapping("/configuration-drifts/{driftId}")
    public ConfigurationDriftResponse getDrift(@PathVariable Long driftId) {
        return driftService.getDrift(driftId);
    }
}
