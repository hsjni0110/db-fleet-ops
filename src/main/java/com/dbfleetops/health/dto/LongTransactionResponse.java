package com.dbfleetops.health.dto;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.domain.LongTransactionInfo;

import java.time.LocalDateTime;

public record LongTransactionResponse(
        Long databaseId,
        DatabaseEngine engine,
        String transactionId,
        String state,
        LocalDateTime startedAt,
        long durationSeconds,
        long threadId,
        String queryPreview
) {
    public static LongTransactionResponse from(
            Long databaseId,
            DatabaseEngine engine,
            LongTransactionInfo info
    ) {
        return new LongTransactionResponse(
                databaseId,
                engine,
                info.transactionId(),
                info.state(),
                info.startedAt(),
                info.durationSeconds(),
                info.threadId(),
                info.queryPreview()
        );
    }
}