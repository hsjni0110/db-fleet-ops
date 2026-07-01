package com.dbfleetops.health.dto;

import com.dbfleetops.database.domain.DatabaseEngine;
import com.dbfleetops.health.domain.LockWaitInfo;

public record LockWaitResponse(
        Long databaseId,
        DatabaseEngine engine,
        String waitingTransactionId,
        long waitingThreadId,
        String waitingQueryPreview,
        String blockingTransactionId,
        long blockingThreadId,
        String blockingQueryPreview
) {
    public static LockWaitResponse from(
            Long databaseId,
            DatabaseEngine engine,
            LockWaitInfo info
    ) {
        return new LockWaitResponse(
                databaseId,
                engine,
                info.waitingTransactionId(),
                info.waitingThreadId(),
                info.waitingQueryPreview(),
                info.blockingTransactionId(),
                info.blockingThreadId(),
                info.blockingQueryPreview()
        );
    }
}