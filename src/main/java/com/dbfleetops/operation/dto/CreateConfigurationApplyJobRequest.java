package com.dbfleetops.operation.dto;

import java.util.List;

public record CreateConfigurationApplyJobRequest(Long profileId, String requestedBy, String reason,
        List<ConfigurationApplyParameterRequest> parameters) {
}
