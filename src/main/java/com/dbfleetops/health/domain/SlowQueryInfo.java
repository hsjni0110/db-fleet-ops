package com.dbfleetops.health.domain;

public record SlowQueryInfo(
        String digestText,
        long executionCount,
        double averageSeconds,
        double maxSeconds,
        long rowsExamined,
        long rowsSent
) {
}