package com.dbfleetops.health.domain;

public record LockWaitInfo(
        String waitingTransactionId,
        long waitingThreadId,
        String waitingQueryPreview,
        String blockingTransactionId,
        long blockingThreadId,
        String blockingQueryPreview
) {
}