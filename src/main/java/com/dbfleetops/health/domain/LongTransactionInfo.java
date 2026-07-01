package com.dbfleetops.health.domain;

import java.time.LocalDateTime;

public record LongTransactionInfo(
        String transactionId,
        String state,
        LocalDateTime startedAt,
        long durationSeconds,
        long threadId,
        String queryPreview
) {
}