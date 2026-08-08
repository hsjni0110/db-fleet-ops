package com.dbfleetops.operation.job.application.required;

/** 설정 점검 후 Job에 기록할 최소 결과입니다. */
public record ConfigurationCheckOutcome(Long driftId, String status) {}
