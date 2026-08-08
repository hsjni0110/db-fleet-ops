package com.dbfleetops.operation.job.application.required;

/** 설정 점검에 필요한 최소 정보입니다. */
public record ConfigurationCheckCommand(Long jobId, Long databaseId, Long profileId,
        String requestedBy) {
}
