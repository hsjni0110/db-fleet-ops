package com.dbfleetops.health.dto;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.domain.SlowQueryInfo;

public record SlowQueryResponse(
        Long databaseId,
        DatabaseEngine engine,
        String digestText,
        long executionCount,
        double averageSeconds,
        double maxSeconds,
        long rowsExamined,
        long rowsSent
) {
    public static SlowQueryResponse from(
            Long databaseId,
            DatabaseEngine engine,
            SlowQueryInfo info
    ) {
        return new SlowQueryResponse(
                databaseId,
                engine,
                info.digestText(),
                info.executionCount(),
                info.averageSeconds(),
                info.maxSeconds(),
                info.rowsExamined(),
                info.rowsSent()
        );
    }
}