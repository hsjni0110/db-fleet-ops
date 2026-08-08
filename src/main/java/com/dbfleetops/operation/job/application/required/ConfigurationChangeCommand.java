package com.dbfleetops.operation.job.application.required;

import java.util.List;

/** 설정 변경에 필요한 최소 정보입니다. */
public record ConfigurationChangeCommand(Long jobId, Long databaseId, Long profileId,
        String requestedBy, String reason, List<ConfigurationChangeItem> items) {
}
