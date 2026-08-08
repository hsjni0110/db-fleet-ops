package com.dbfleetops.operation.job.dto;

import java.util.List;

public record CreateConfigurationApplyJobRequest(Long profileId, String requestedBy, String reason,
        List<ConfigurationApplyParameterRequest> parameters) {
}
