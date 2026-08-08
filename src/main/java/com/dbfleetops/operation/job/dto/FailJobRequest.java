package com.dbfleetops.operation.job.dto;

public record FailJobRequest(
        String resultCode,
        String resultMessage,
        boolean retryable
) {
}
