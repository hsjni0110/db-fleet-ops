package com.dbfleetops.operation.job.dto;

import java.util.List;

public record ConfigurationApplyJobPayload(Long profileId, String reason, String requestedBy,
        List<ConfigurationApplyJobParameterPayload> parameters) {
}
