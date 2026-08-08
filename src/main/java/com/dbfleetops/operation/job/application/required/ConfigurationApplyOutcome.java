package com.dbfleetops.operation.job.application.required;

/** 설정 적용 후 Job 성공 여부와 집계를 전달합니다. */
public record ConfigurationApplyOutcome(Long applyId, boolean succeeded, String status,
        int successCount, int failedCount, int skippedCount) {}
