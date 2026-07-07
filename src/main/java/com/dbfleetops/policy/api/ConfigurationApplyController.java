package com.dbfleetops.policy.api;

import com.dbfleetops.policy.application.ConfigurationApplyQueryService;
import com.dbfleetops.policy.dto.ConfigurationApplyResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ConfigurationApplyController {

    private final ConfigurationApplyQueryService applyQueryService;

    public ConfigurationApplyController(ConfigurationApplyQueryService applyQueryService) {
        this.applyQueryService = applyQueryService;
    }

    @GetMapping("/configuration-applies/{applyId}")
    public ConfigurationApplyResponse getApply(@PathVariable Long applyId) {
        return applyQueryService.getApply(applyId);
    }

    @GetMapping("/jobs/{jobId}/configuration-apply")
    public ConfigurationApplyResponse getApplyByJobId(@PathVariable Long jobId) {
        return applyQueryService.getApplyByJobId(jobId);
    }
}
