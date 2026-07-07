package com.dbfleetops.operation.dto;

import java.util.List;

public record ConfigurationApplyJobPayload(Long profileId, String reason, String requestedBy,
        List<ConfigurationApplyJobParameterPayload> parameters) {
}
